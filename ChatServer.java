import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

class Chat{
    //Room TreeSet containing all the rooms in the chat
    TreeSet<Room> rooms = new TreeSet<>();
    //User Treeset containting all users connected to the chat (for username verification O(log(n)))
    TreeSet<User> users = new TreeSet<>();

    ByteBuffer buffer;

    Chat(){
        this.rooms = new TreeSet<Room>();
        this.users = new TreeSet<User>();
        // A pre-allocated buffer for the received data
        buffer = ByteBuffer.allocate( 16384 );
    }

    boolean containsNickname(String n){
        return users.contains(new User(n));
    }

    void addUserToRoom(User user, Room room) throws IOException{
        if(this.rooms.contains(room) == false){
            room.addUser(user); //Add user to room
            user.chatRoom = room; //Setting user chatroom to the current room
            this.rooms.add(room); //Add chatroom to global variable rooms
        }
        else{
            //Loop through all rooms, find the one the user wants to enter and add them to it
            for(Room r : rooms){
                if(r.compareTo(room)==0){//If found the room
                    r.addUser(user); //Add user to it
                    user.chatRoom = r; //Update user chatRoom attribute with current room
                    notifyMembers(user, r, "JOIN");
                    break;
                }
            }
        }
        //Change user state to "inside"
        user.currState = user.states[2];
        
    }

    void removeUserFromRoom(User user, Room room) throws IOException{
        //remove user from room
        room.users.remove(user);

        //If room is empty, delete it from the global list of rooms
        if(room.isEmpty()) 
            this.rooms.remove(user.chatRoom);
        else //else, notify members that user left 
            notifyMembers(user, room, "LEFT");
        
        //delete room attribute on user object
        user.setRoom(null);
    }

    void removeUserFromChat(User user){
        this.users.remove(user);
    }

    //Method that notifies current members of a chatroom the fact a new user just entered the room
    private void notifyMembers(User user, Room room, String action) throws IOException{
        ByteBuffer buffer = ByteBuffer.allocate( 16384 );
        String message = "";
        if(action.equals("JOIN"))
            message = "JOINED "+user+"\n";
        else if (action.equals("LEFT"))
            message = "LEFT "+user+"\n";

        //Send message to all users but the one who joined/left the chat room
        for(User u : room.users)
            if(u.compareTo(user)!=0)
                this.sendMessageToClient(message, buffer, u.socketChannel);
        
    }

    void notifyChangeNickname(User user, Room room, String oldNickname, String newNickname) throws IOException{
        ByteBuffer buffer = ByteBuffer.allocate( 16384 );
        String message = "NEWNICK "+oldNickname+" "+newNickname+"\n";
        
        //Send message to all users but the one who made the change
        for(User u : room.users)
            if(u.compareTo(user)!=0)
                this.sendMessageToClient(message, buffer, u.socketChannel);
    }

    //Method that sends message from the server to the client using the given buffer
    void sendMessageToClient(String message, ByteBuffer buffer, SocketChannel clientChannel) throws IOException{
        buffer.clear();
        buffer.put(message.getBytes());
        buffer.flip();
        
        while(buffer.hasRemaining()) {
            clientChannel.write(buffer);
        }
    }
}

class Room implements Comparable<Room>{
    ArrayList<User> users; //List of users who are in the chat room
    String roomName; //Unique String identifier of a room (used for comparison)

    Room(String n){
        this.roomName = n;
        users = new ArrayList<User>();
    }

    //Adding User into the room
    void addUser(User u){
        users.add(u);
    }

    boolean isEmpty(){
        return users.size()==0;
    }

    //Comparisons between rooms are made based on their name (there cannot be two different rooms with the same name)
    public int compareTo(Room r){
        return this.roomName.compareTo(r.roomName);
    }

    public String toString(){
        return roomName+ ": "+users;
    }
}


class User implements Comparable<User>{
    String nickname; //User chosen nickname
    String[] states = {"init", "outside", "inside"}; //3 possible states user can be
    String currState; //State the user is currently on
    String partialCommand; //String with partial commands from the user
    Queue<String> commands;//Queue with valid and complete commands from user
    SocketChannel socketChannel;//Socket channel that identifies user 
    Room chatRoom; //Chat room in which the user is currently on (if the user in not in any room, this is null)

    //Strictly for searching for users with nickname n
    User(String n){
        this.nickname = n;
    }
    
    User(SocketChannel c){
        this.socketChannel = c;
        this.chatRoom = null;
        this.currState = states[0];
        partialCommand = "";
        commands = new LinkedList<String>();
    }

    void setNickname(String n){
        this.nickname = n.split("\n")[0];

        //When user is not on a chat room, change the state from init to outside. If user is already in a room, don't change states
        if(!currState.equals("inside"))
            this.currState = states[1];
    }

    void setRoom(Room r){
        this.chatRoom = r;
    }
    
    void processClientMessage(String m){
        if(m.equals("\n"))//If user only sent break line char, do nothing
            return;
        
        for(int i=0;i<m.length();i++){
            char c = m.charAt(i);
            if(c == '\n'){
                if(!partialCommand.equals(""))
                    commands.add(partialCommand);
                    partialCommand = "";
            }
            else
            partialCommand += c;
        }
    }

    void executeCommands(Chat chat) throws IOException{
        //loop through commands queue and process all valid commands
        while(!commands.isEmpty()){
            String command = commands.poll();//get first value on queue
            String response = "";
            if(command.charAt(0)!='/' && currState.equals("init")){
                response = "ERROR - Trying to send message without username\n";
            }
            else if(command.charAt(0)!='/' && currState.equals("outside")){
                response = "ERROR - Trying to send message outside of chat room\n";
            }
            else{
                if(command.charAt(0)=='/'){//that means user is sending commands
                    response = processCommand(chat, command);
                }
                else{ //otherwise, user is sending message inside a chatroom
                    //loop through all users in the room and write to their socket channel the message
                    String message = "MESSAGE "+nickname+" "+command+"\n"; //user message

                    for(User u : chatRoom.users){
                        chat.sendMessageToClient(message,chat.buffer,u.socketChannel);
                    }
                }
            }

            //In the end, send the server response to client if user is not null
            chat.sendMessageToClient(response,chat.buffer,socketChannel);

            if(socketChannel != null){
                System.out.println("Message received from user " + socketChannel.getRemoteAddress()+" ("+nickname+")"); 
                chat.buffer.clear();
            }
        }
    }

    private String processCommand(Chat chat, String command) throws IOException{
        String[] fields = command.split(" ");
        String response="";
        switch(fields[0]){
            case "/nick":
                if(chat.users.contains(new User(fields[1]))){
                    response = "ERROR - Nickname not available\n";
                }
                else{
                    //if user is already inside a chat room, alert all other users of the change in nickname
                    if(currState.equals("inside"))
                    chat.notifyChangeNickname(this, chatRoom, nickname, fields[1]);
                        
                    //response is OK
                    response = "OK\n";
                    
                    //attach nickname to connection
                    setNickname(fields[1]);

                    //add user to global variable users after they choose username
                    chat.users.add(this);
                }
                
                break;
            case "/join":
                //if user doesn't have a nickname and tries to create a room, return error
                if(currState.equals("init")){
                    response = "ERROR - You have to have a nickname to enter a chatroom\n";
                    break;
                }
                
                if(currState.equals("inside")){//if user is already in a room and want to join another, get out of the one he/she is at first
                    chat.removeUserFromRoom(this,chatRoom);
                }
        
                //create room if it doesn't exist and add user to it
                Room room = new Room(fields[1]);
                chat.addUserToRoom(this,room);
                response = "OK\n";
                break;

            case "/leave":
                chat.removeUserFromRoom(this,chatRoom);
                
                //update user state from "inside" to "outside" (I have to create a method inside the User class which is only responsible to change the user state)
                currState = states[1];
                
                //sending OK response to client
                response = "OK\n";
                
                break;
            case "/bye":
                if(currState.equals("inside"))
                    chat.removeUserFromRoom(this,chatRoom);//remove user from curr room
                chat.removeUserFromChat(this); //remove user from users list
                chat.sendMessageToClient("BYE\n", chat.buffer,socketChannel);//send BYE message
                socketChannel.close();//server closes connection
                System.out.println("User" + socketChannel.getRemoteAddress()+"("+nickname+") has disconnected.");
                socketChannel = null;//removing socketChannel for the user who left the chat
                break;
            default:
                
                // If user is sending a message starting with / and it's not a command, then the '/' must be escaped in the client and sent to the server. For example, if user 'test' inside a room 'abc' sends a message "//commmand", the client sees this, doesn't recognizes it as a valid command and adds a '/' in the beginning, sending the message to the server. Then the server sees perceives the escaping the removes the first "/" of the message and then sends it to the client as "/command"!
                if(currState.equals("inside") && command.charAt(1)=='/'){//user is inside a chat room and the command was escaped by the client
                    String message = "MESSAGE "+nickname+" "+command.substring(1,command.length())+"\n";//escaping / character

                    for(User u : chatRoom.users){//sending message to all users in the chat room
                        chat.sendMessageToClient(message,chat.buffer,u.socketChannel);
                    }
                    response = "OK\n"; 
                }
                else{
                    response = "ERROR - Non-existing command\n";//user is tryint to use non-existing command
                }
        }
        return response;
      }
    
    public int compareTo(User u){
        return this.nickname.compareTo(u.nickname);
    }

    public String toString(){
	    return nickname;
    }
}



public class ChatServer {
    //global variable chat containing properties and methods for functionalities in the chat (verifying if nickname can be used, removing empty rooms, etc)
    static Chat chat = new Chat();

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    private final ServerSocketChannel serverChannel;
    private final Selector selector;

    private static int PORT;

    static public void main(String args[]) throws Exception {
      	// Parse port from command line
      	PORT = Integer.parseInt( args[0] );
        try {
          ChatServer server = new ChatServer();
          server.start();
        } catch(IOException e) {
           System.err.println("Server Error: " + e.getMessage());
        }
     
    }

    public ChatServer() throws IOException {
      selector = Selector.open();
      serverChannel = ServerSocketChannel.open();
      serverChannel.configureBlocking(false);

      /* Registra o canal para ser monitorado pelo selector quando operações
       *  de solicitação de aceitação de conexão de clientes (OP_ACCEPT) ocorrerem.
       *  As operações de leitura de dados enviados pelos clientes são monitoradas
       *  nos canais de cada cliente.
       */
       serverChannel.register(selector, SelectionKey.OP_ACCEPT);

       // Indica que o servidor vai ficar escutando em um determinado IP/Nome DNS e Porta.
       serverChannel.bind(new InetSocketAddress(PORT));
    }

    public void start() {
        System.out.println( "Listening on port "+PORT );
        while(true) {
            try {
                selector.select();
                processEvents(selector.selectedKeys());
            } 
            catch(IOException e) {
                    System.err.println(e.getMessage());
            }
        }
    }

     private void processEvents(Set<SelectionKey> selectionKeys) throws IOException {
         Iterator<SelectionKey> iterator = selectionKeys.iterator();
         while (iterator.hasNext()) {
             //Obtém o evento a ser processado
             SelectionKey selectionKey = iterator.next();

             /*Remove a SelectionKey da lista para indicar que
              um evento da mesma foi processado*/
             iterator.remove();

             /*Se o evento foi cancelado ou a conexão fechada, a selectionKey fica inválida
             e assim não será processada.*/
             /*if (!selectionKey.isValid()) {
                //SocketChannel clientChannel = (SocketChannel) selectionKey.channel();
                 System.out.println("Client "+selectionKey.attachment()+" disconnected!");
                 continue;
             }*/
             if (selectionKey.isAcceptable()) {
                 processAcceptable(selectionKey);
             } 
             else if (selectionKey.isReadable()) {
                 processReadable(selectionKey);
             }
         }
     }

     private void processAcceptable(SelectionKey selectionKey) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        System.out.println("Cliente " + clientChannel.getRemoteAddress() + " conectado.");

        clientChannel.configureBlocking(false);
        /*A operação só bloqueia se tiver outro thread gravando no mesmo canal.
        * Como não temos vários threads no servidor, então não teremos tal situação.*/
        clientChannel.write(ByteBuffer.wrap("Welcome to the Chat.\n".getBytes()));

        /*Registra o canal para ser monitorarado pelo selector quando
        dados enviados pelo cliente estiverem disponíveis para serem lidos*/
        clientChannel.register(selector, SelectionKey.OP_READ);
     }

     private void processReadable(SelectionKey selectionKey) throws IOException {
        SocketChannel clientChannel = (SocketChannel) selectionKey.channel();
        chat.buffer.clear();
        //Armazena o total de bytes da mensagem recebida do cliente
        int bytesRead;
        try {
            //Reads message from client and adds it to buffer
            bytesRead = clientChannel.read(chat.buffer);
        } 
        catch (IOException e) {
            System.err.println("Couldn't read data. Connection closed by client " + clientChannel.getRemoteAddress() + ": " + e.getMessage());
            clientChannel.close();
            selectionKey.cancel();
            return;
        }
        if(bytesRead <= 0) return;
        chat.buffer.flip();

        //adding User object into key attachment to distinguish users from each other
        if(selectionKey.attachment()==null){
            selectionKey.attach(new User(clientChannel));
        }
        
        User user = (User) selectionKey.attachment();
        String messageFromClient = decoder.decode(chat.buffer).toString();//decodes buffer and returns string

        //process user command from message from client
        user.processClientMessage(messageFromClient);

        //execute all valid commands
        user.executeCommands(chat);
      }
}
