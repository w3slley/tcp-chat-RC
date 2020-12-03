import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;


public class ChatClient {


    // VariÃ¡veis relacionadas com a interface grÃ¡fica --- * NÃƒO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variaveis relacionadas coma interface grÃ¡fica

    // Se for necessario adicionar variaveis ao objecto ChatClient, devem
    // ser colocadas aqui
    private final SocketChannel sc;
    private final CharsetDecoder decoder;
    private String Umessage;



    // Metodo a usar para acrescentar uma string à caixa de texto
    public void printMessage(final String message) {
        //chatArea.append(message);
        Scanner s = new Scanner (message);
        String temp, cmd;
        if (s.hasNext()) {
            temp = s.next();
            if (temp.equals("OK")) {
                String str = "";
                Scanner slm = new Scanner (Umessage);
                cmd = slm.next();

                if (cmd.equals("/nick")) {
                    str = "User: " + slm.next() + "\n";
                }
                else if (cmd.equals("/join")) {
                    str = "Joined room: \"" + slm.next() + "\".\n";
                }
                else if (cmd.equals("/leave")) {
                    str = "Left Room\n";
                }
                /*else if (cmd.equals("/priv")) {
                    str = "(Mensagem privada para) " + slm.next() + ": " + slm.nextLine() + "\n";
                } */
                /*else {
                    str = "";
                } */
                slm.close();
                chatArea.append(str);
            }
            else if (temp.equals("ERROR")) {
                String str = "";
                Scanner slm = new Scanner (Umessage);
                cmd = slm.next();

                if (cmd.equals("/nick")) {
                    str = "This nickname is already in use\n";
                }
                else if (cmd.equals("/leave")) {
                    str = "Join a room\n";
                }
                /* else if (cmd.equals("/priv")) {
                    str = "O destinatário escolhido não existe.\n";
                } */
                else {
                    str = "Choose a name and Join a room\n";
                }
                slm.close();
                chatArea.append(str);
            }
            else if (temp.equals("MESSAGE")) {
                StringBuilder str = new StringBuilder();
                str.append(s.next());
                str.append(":");
                str.append(s.nextLine());
                str.append("\n");
                chatArea.append(str.toString());
            }
            else if (temp.equals("JOINED")) {
                StringBuilder str = new StringBuilder();
                str.append("\"" + s.next() + "\"");
                str.append(" joined room.\n");
                chatArea.append(str.toString());
            }
            else if (temp.equals("NEWNICK")) {
                StringBuilder str = new StringBuilder();
                str.append("\"" + s.next() + "\"");
                str.append(" new nickname: ");
                str.append("\"" + s.next() + "\"");
                str.append(".\n");
                chatArea.append(str.toString());
            }
            else if (temp.equals("LEFT")) {
                StringBuilder str = new StringBuilder();
                str.append("\"" + s.next() + "\"");
                str.append(" left room.\n");
                chatArea.append(str.toString());
            }
            else if (temp.equals("PRIVATE")) {
                StringBuilder str = new StringBuilder();
                str.append("(Private) ");
                str.append(s.next());
                str.append(":");
                str.append(s.nextLine());
                str.append("\n");
                chatArea.append(str.toString());
            }
            else if (temp.equals("BYE")) {
                chatArea.append("Connection Closed\n");
            }
            else {
                chatArea.append(message);
            }
        }
        s.close();
    }


    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicializaçao da interface grafica --- * NAO MODIFICAR *
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

        sc = SocketChannel.open(new InetSocketAddress(server, port));
        sc.configureBlocking(true);
        decoder = Charset.forName("UTF8").newDecoder();

        // --- Fim da inicializaÃ§Ã£o da interface grafica

        // Se for necessÃ¡rio adicionar codigo de inicializaçao ao
        // construtor, deve ser colocado aqui
        Umessage = null;
    }


    // Metodo invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com cÃ³digo que envia a mensagem ao servidor
        Umessage = message;
        if (message.startsWith("/")) {
            if (message.startsWith("/nick ") ||
                    message.startsWith("/priv ") ||
                    message.equals("/leave") ||
                    message.equals("/bye") ||
                    message.startsWith("/join ")) {
            }
            else {
                message = "/" + message;
            }
        }
        sc.write(ByteBuffer.wrap((message + "\n").getBytes("UTF-8")));

    }


    // Metodo principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
        ByteBuffer buffer = ByteBuffer.allocate(16384);
        printMessage("Connected!\n");
        String str;
        while (true) {
            try {
                sc.read(buffer);
            }
            catch (IOException e) {
                e.printStackTrace();
                break;
            }
            buffer.flip();
            str = decoder.decode(buffer).toString();
            printMessage(str);
            if (str.equals("BYE\n")) {
                sc.socket().close();
                break;
            }
            buffer.clear();
            buffer.rewind();
        }
        frame.dispose(); // termina a interface grafica


    }


    // Instancia o ChatClient e arranca-o invocando o seu mÃ©todo run()
    // * NAO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
