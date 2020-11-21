import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

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

    //Comparisons between rooms are made based on their name (there cannot be two different rooms with the same name)
    public int compareTo(Room r){
        return this.roomName.compareTo(r.roomName);
    }

    public String toString(){
        return users+"";
    }

}


class User implements Comparable<User>{
    String nickname; //User chosen nickname
    String[] states = {"init", "outside", "inside"}; //3 possible states user can be
    String currState; //State the user is currently on
    String command; //String with partial commands from the user
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
        command = "";
        commands = new LinkedList<String>();
    }

    void setNickname(String n){
        this.nickname = n.split("\n")[0];
        this.currState = states[1];//when user inserts username, the default state is "init"
    }

    void setRoom(Room r){
        this.chatRoom = r;
    }
    
    void processClientMessage(String m){
        for(int i=0;i<m.length();i++){
            char c = m.charAt(i);
            if(c == '\n'){
                commands.add(command);
                command = "";
            }
            else
                command += c;
        }
    }
    
    public int compareTo(User u){
        return this.nickname.compareTo(u.nickname);
    }

    public String toString(){
	    return nickname;
    }
}

class Chat{
    //Room TreeSet containing all the rooms in the chat
    TreeSet<Room> rooms = new TreeSet<>();
    //User Treeset containting all users connected to the chat (for username verification O(log(n)))
    TreeSet<User> users = new TreeSet<>();

    Chat(){
        this.rooms = new TreeSet<Room>();
        this.users = new TreeSet<User>();
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
                    notifyMembers(user, r);
                    break;
                }
            }
        }
        //Change user state to "inside"
        user.currState = user.states[2];
        
    }

    //Method that notifies current members of a chatroom the fact a new user just entered the room
    private void notifyMembers(User user, Room room) throws IOException{
        ByteBuffer buffer = ByteBuffer.allocate( 16384 );
        for(User u : room.users){
            String message = "JOINED "+user+"\n";
            this.sendMessageToClient(message, buffer, u.socketChannel);
        }
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

public class ChatServer {
    //global variable chat containing properties and methods for functionalities in the chat (verifying if nickname can be used, removing empty rooms, etc)
    static Chat chat = new Chat();

    // A pre-allocated buffer for the received data
    static private  ByteBuffer buffer;
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
           System.err.println("Erro durante execução do servidor: " + e.getMessage());
        }
     
    }

    public ChatServer() throws IOException {
      // Cria um buffer de um tamanho definido (em total de bytes)
      buffer = ByteBuffer.allocate( 16384 );

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
                } catch(IOException e) {
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
             if (!selectionKey.isValid()) {
                //SocketChannel clientChannel = (SocketChannel) selectionKey.channel();
                 System.out.println("Client "+selectionKey.attachment()+" disconnected!");
                 continue;
             }
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
        buffer.clear();

        //Armazena o total de bytes da mensagem recebida do cliente
        int bytesRead;
        try {
            //Recebe (lê) uma mensagem do cliente e armazena dentro do buffer
            bytesRead = clientChannel.read(buffer);
        } catch (IOException e) {
            System.err.println(
                    "Não pode ler dados. Conexão fechada pelo cliente " +
                    clientChannel.getRemoteAddress() + ": " + e.getMessage());
            clientChannel.close();
            selectionKey.cancel();
            return;
        }
        if(bytesRead <= 0)
            return;
        buffer.flip();

        //Vetor que armazenará os dados lidos do buffer
        byte[] data = new byte[bytesRead];
        
    
        //adding User object into key attachment to distinguish users from each other
        if(selectionKey.attachment()==null){
            selectionKey.attach(new User(clientChannel));
        }
        
        User user = (User) selectionKey.attachment();
        String messageFromClient = decoder.decode(buffer).toString();//decodes buffer and returns string

        //process user command (add into queue if it's a valid command (or commands), otherwise build up command until it reaches a '/n', and then add it to queue)
        user.processClientMessage(messageFromClient);

        //loop through commands queue and process all valid commands
        while(!user.commands.isEmpty()){
            String command = user.commands.poll();//get first value on queue
            String[] fields = command.split(" ");
            String response = "";
            
            if(command.charAt(0)!='/' && user.currState == "init"){
                response = "It is not possible to write messages if you haven't choosen a nickname yet! \n";
            }
            else if(command.charAt(0)!='/' && user.currState == "outside"){
                response = "You are not inside a chat room! \n";
            }
            else{
                if(command.charAt(0)=='/'){//that means user is sending commands
                    switch(fields[0]){
                        case "/nick":
                            //response is OK
                            response = "OK - Your nickname has been set. \n";
                            //attach nickname to connection
                            user.setNickname(fields[1]);
                            //add user to global variable users after they choose username
                            chat.users.add(user);
                            break;
                        case "/join":
                            //if user doesn't have a nickname and tries to create a room, return error
                            if(user.currState == "init"){
                                response = "ERROR - You cannot enter a room without inserting a nickname! \n";
                                break;
                            }
                    
                            //create room if doesn't exist and add user to it
                            Room room = new Room(fields[1]);
                            chat.addUserToRoom(user,room);
                            response = "User @"+user+" entered room #"+fields[1]+"\n";
                            break;
                        case "/leave":
                            break;
                        case "/bye":
                            break;
                        default:
                            response = "Error - Not a valid command. \n"; 
                    }
                }
                else{ //otherwise, user is sending message inside a chatroom

                    //loop through all users in the room and write to their socket channel the message
                    String message = "MESSAGE "+user+" "+command+"\n"; //user message

                    for(User u : user.chatRoom.users){
                        chat.sendMessageToClient(message,buffer,u.socketChannel);
                    }
                }
            }
            
            //In the end, send the server response to client
            chat.sendMessageToClient(response,buffer,clientChannel);
    
            
            /*
            * Como data é um vetor de bytes, mas nossa mensagem é uma String,
            * precisamos converter tal vetor para String.
            * Neste caso, basta chamar o construtor da classe String passando
            * tal vetor por parâmetro.
            */
            System.out.println("Message received from user " + clientChannel.getRemoteAddress() + ": " + new String(data) + " (" + bytesRead + " bytes lidos)"); 
            buffer.clear();
        }
      }
}
