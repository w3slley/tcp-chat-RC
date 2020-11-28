import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class ChatClient implements Runnable{

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    private final Scanner scanner;
    private final Selector selector;
    private final SocketChannel clientChannel;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);


    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }


    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocus();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
        selector = Selector.open();
        clientChannel = SocketChannel.open();
        clientChannel.configureBlocking(false);


        //registra todas as operações válidas para um SocketChanel.
        //clientChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        clientChannel.register(selector, clientChannel.validOps());

        scanner = new Scanner(System.in);


    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {


    }

    // Entra no loop de envio de mensagens pro servidor.
    private void sendMessageLoop() throws IOException {
         String msg;
         do {
             System.out.print("Digite uma mensagem (ou sair para finalizar): ");
             msg = scanner.nextLine();
             clientChannel.write(ByteBuffer.wrap(msg.getBytes()));
         }while(!msg.equalsIgnoreCase("sair"));
    }

    // Processa mensagens recebidas do servidor.
    private void processRead() throws IOException {
        buffer.clear();
        //O método read lê os dados e guarda dentro do buffer.
        int bytesRead = clientChannel.read(buffer);

        /*Altera o buffer do modo de gravação (cuja posição
         atual indica a última posição preenchida) para o modo de leitura
         (resetando a posição inicial para 0 para permitir ler os dados desde o início do buffer).*/
        buffer.flip();
        if (bytesRead > 0) {
            byte data[] = new byte[bytesRead];
            buffer.get(data);
            System.out.println(
                    "Mensagem recebida do servidor: " + new String(data));
        }
    }

    /*
     * Processa a aceitação da conexão do cliente pelo servidor,
     * que indica que o cliente conectou com sucesso.
     * @throws IOException
     */
    private void processConnectionAccept() throws IOException {
        System.out.println("Cliente conectado ao servidor");
        if(clientChannel.isConnectionPending()) {
            clientChannel.finishConnect();
        }
        System.out.print("Digite seu login: ");
        String login = scanner.nextLine();
        clientChannel.write(ByteBuffer.wrap(login.getBytes()));
    }

    /*
     * Método a ser executado num novo Thread
     * para ficar a espera de mensagens enviadas pelo servidor.
     */
     public void start() {
         try {
             /*Espera por eventos por no máximo 1 segundo */
             while (selector.select(1000) > 0) {
                 Set<SelectionKey> selectionKeys = selector.selectedKeys();
                 Iterator<SelectionKey> iterator = selectionKeys.iterator();
                 while (iterator.hasNext()) {
                     SelectionKey selectionKey = iterator.next();
                     if (selectionKey.isReadable())
                         processRead();
                     iterator.remove();
                 }
             }
         }catch(IOException e){
             System.err.println("Erro ao ler dados enviados pelo servidor: " + e.getMessage());
         }
     }

    // Método principal do objecto
    // Inicia o processo de espera pela conexão com o servidor e envio e recebimento de mensagens.
    @Override
    public void run() throws IOException {
      try {
          /* Espera pelo primeiro evento, que só pode ser indicando o sucesso da conexão.
          *  O método bloqueia até uma resposta ser obtida ou timeout ocorrer
          *  após 1 segundo. */
          selector.select(1000);
          processConnectionAccept();

          /* Cria um novo thread para ficar aguardando mensagens enviadas pelo servidor,
           *  paralelamente ao envio de mensagens.
           */
          new Thread(this).run();
          sendMessageLoop();
      }finally{
          clientChannel.close();
          selector.close();
      }
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
