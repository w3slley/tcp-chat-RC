import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
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
      /*	try {
    	    // Instead of creating a ServerSocket, create a ServerSocketChannel
    	   ssc = ServerSocketChannel.open();
    	    // Set it to non-blocking, so we can use select
    	    ssc.configureBlocking( false );
    	    // Get the Socket connected to this channel, and bind it to the
    	    // listening port
    	    ServerSocket ss = ssc.socket();
    	    InetSocketAddress isa = new InetSocketAddress( port );
    	    ss.bind( isa );
    	    // Create a new Selector for selecting
    	    Selector selector = Selector.open();

    	    // Register the ServerSocketChannel, so we can listen for incoming
    	    // connections
    	    ssc.register( selector, SelectionKey.OP_ACCEPT );
    	    System.out.println( "Listening on port "+port );
    	    while (true) {
    		// See if we've had any activity -- either an incoming connection,
    		// or incoming data on an existing connection
    		int num = selector.select();
    		// If we don't have any activity, loop around and wait again
    		if (num == 0)
    		    continue;
    		// Get the keys corresponding to the activity that has been
    		// detected, and process them one by one
    		Set<SelectionKey> keys = selector.selectedKeys();
    		Iterator<SelectionKey> it = keys.iterator();
    		while (it.hasNext()) {
    		    // Get a key representing one of bits of I/O activity
    		    SelectionKey key = it.next();
    		    // What kind of activity is it?
    		    if (key.isAcceptable()) {
    			// It's an incoming connection.  Register this socket with
    			// the Selector so we can listen for input on it
    			Socket s = ss.accept();
    			System.out.println( "Got connection from "+s );
    			// Make sure to make it non-blocking, so we can use a selector
    			// on it.
    			SocketChannel sc = s.getChannel();
    			sc.configureBlocking( false );
    			//attaching nickname to user (next modification will be this)
    			//Nickname n = new Nickname("test");
    			//key.attach(n);

    			// Register it with the selector, for reading
    			sc.register( selector, SelectionKey.OP_READ );
    		    } else if (key.isReadable()) {
    			SocketChannel sc = null;
    			try {
    			    // It's incoming data on a connection -- process it
    			    sc = (SocketChannel) key.channel();
    			    //Distribute message to all other connection sockets
    			    boolean ok = processInput(selector, sc);
    			    // If the connection is dead, remove it from the selector
    			    // and close it
    			    if (!ok) {
    				key.cancel();
    				Socket s = null;
    				try {
    				    s = sc.socket();
    				    System.out.println( "Closing connection to "+s );
    				    s.close();
    				} catch( IOException ie ) {
    				    System.err.println( "Error closing socket "+s+": "+ie );
    				}
    			    }

    			} catch( IOException ie ) {
    			    // On exception, remove this channel from the selector
    			    key.cancel();
    			    try {
    				sc.close();
    			    } catch( IOException ie2 ) { System.out.println( ie2 ); }
    			    System.out.println( "Closed "+sc );
    			}
    		    }
    		}
    		// We remove the selected keys, because we've dealt with them.
    		keys.clear();
    	    }
    	} catch( IOException ie ) {
    	   System.err.println( ie );
    	} */
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
                 continue;
             }
             if (selectionKey.isAcceptable()) {
                 processConnectionAccept();
             } else if (selectionKey.isReadable()) {
                 processRead(selectionKey);
             }
         }
     }

     private void processConnectionAccept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        System.out.println("Cliente " + clientChannel.getRemoteAddress() + " conectado.");

        clientChannel.configureBlocking(false);
        /*A operação só bloqueia se tiver outro thread gravando no mesmo canal.
        * Como não temos vários threads no servidor, então não teremos tal situação.*/
        clientChannel.write(ByteBuffer.wrap("Bem vindo ao chat.\n".getBytes()));

        /*Registra o canal para ser monitorarado pelo selector quando
        dados enviados pelo cliente estiverem disponíveis para serem lidos*/
        clientChannel.register(selector, SelectionKey.OP_READ);
     }

     private void processRead(SelectionKey selectionKey) throws IOException {
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

        if(bytesRead <= 0){
            return;
        }
        buffer.flip();

        //Vetor que armazenará os dados lidos do buffer
        byte[] data = new byte[bytesRead];
      /*  String message = decoder.decode(buffer).toString();
      	String[] ans = message.split(" ");
        if(ans[0].equals("/nick")){
      	    //mudar estado do cliente com nome  ans[1]
      	    //enviar OK
      	    //Improve this later
      	    String response = "OK \n";
            buffer.put(response.getBytes());
          } */
        buffer.get(data);

        /*
         * Como data é um vetor de bytes, mas nossa mensagem é uma String,
         * precisamos converter tal vetor para String.
         * Neste caso, basta chamar o construtor da classe String passando
         * tal vetor por parâmetro.
         */
       System.out.println(
            "Mensagem recebida do cliente " +
            clientChannel.getRemoteAddress() + ": " + new String(data) +
            " (" + bytesRead + " bytes lidos)"); 
        buffer.clear();
      }

    // Just read the message from the socket and send it to stdout
  /*  static private boolean processInput(Selector selector, SocketChannel sc ) throws IOException {
      	Set<SelectionKey> keys = selector.keys(); //Selector.keys() returns all the active connections in the selector
      	// Read the message to the buffer
      	buffer.clear();
      	sc.read( buffer );
      	buffer.flip();
      	// If no data, close the connection
      	if (buffer.limit()==0)
      	    return false;
      	//loop through buffer and use the method SocketChannel.write() to send back buffer to connection who sent it.
      	String message = decoder.decode(buffer).toString();
      	String[] ans = message.split(" ");
      	if(ans[0].equals("/nick")){
      	    //mudar estado do cliente com nome  ans[1]
      	    //enviar OK
      	    //Improve this later
      	    String response = "OK \n";
      	    buffer.clear();
      	    buffer.put(response.getBytes());
      	    buffer.flip();

      	    while(buffer.hasRemaining()) {
      		sc.write(buffer);
      	    }
      	}
      	else if(ans[0].equals("/join")){
      	    //ans[1] is the name of the room.
      	}
      	else if(ans[0].equals("/bye")){

        } */

    	//loop through keys and send buffer only to active connections
    	/*Iterator<SelectionKey> it = keys.iterator();
    	while(it.hasNext()){
    	    SelectionKey key = it.next();
    	    if(!key.isAcceptable()){//only if the connection is not being established for the first time
    		// Decode and print the message to stdout
    		String message = decoder.decode(buffer).toString();
    		String[] ans = message.split(" ");
    		System.out.print(Arrays.toString(ans));
    		SocketChannel SCothers = (SocketChannel) key.channel();
    		while(buffer.hasRemaining()){
    		    SCothers.write(buffer);//write buffer to all active connections
    		}
    		//you have to rewind the buffer so that it can be used again
    		buffer.rewind();
    	    }
    	}
    	return true;
    } */
}
