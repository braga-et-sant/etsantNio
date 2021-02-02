import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.concurrent.TimeUnit;

public class ChatClient implements Runnable {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    String Gserver;
    int Gport;
    InetAddress ip;
    Socket clientSocket;

    // threads de proxy usadas para rodar instacias de client
    class clientThread implements Runnable {
        private Thread t;
        private String threadName;
        public volatile boolean running = true;

        public void terminate() {
            running = false;
        }

        clientThread(String name) {
            threadName = name;
            // System.out.println("Creating " + threadName);
        }

        // uma instancia para ler e uma para escrever
        public void run() {
            // System.out.println("Running " + threadName);

            // essa se prende no loop dentro de readInput
            if (threadName.equals("Send") && running) {
                System.out.println("Send thread is up and running.");
                readInput();
            }
            // enquanto esse executa normalmente, esperando input do utilizador para ser
            // entregue
            // via newMessage
            if (threadName.equals("Receive") && running) {
                System.out.println("Receive thread is up and running. ");

            }
        }

        public void start() {
            // System.out.println("Starting " + threadName);
            if (t == null) {
                t = new Thread(this, threadName);
                t.start();
            }
        }
    }

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
        Gserver = server;
        Gport = port;

    }

    public String checkOut(String message) {
        if (message == null)
            return message;
        String[] tudo = message.split(" ", 2);

        if (tudo[0].charAt(0) == '/') {
            if (tudo[0].equals("/bye") || tudo[0].equals("/nick") || tudo[0].equals("/leave") || tudo[0].equals("/join")
                    || tudo[0].equals("/priv"))
                return message;
            else
                return "/" + message;
        }
        return message;
    }

    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        String checkedOutput = "";
        // System.out.println(message);
        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
        checkedOutput = checkOut(message);
        System.out.println("To server: " + checkedOutput);
        outToServer.writeBytes(checkedOutput + '\n');
    }

    public String translate(String message) {
        if (message == null)
            return "\n";
        String[] tudo = message.split(" ", 3);

        if (tudo.length < 2)
            return message;

        if (tudo[0].equals("JOINED")) {
            return (tudo[1] + " has joined your current room.");
        } else if (tudo[0].equals("LEFT")) {
            return (tudo[1] + " has left your current room.");
        }

        if (tudo.length != 3)
            return message;

        if (tudo[0].equals("MESSAGE")) {
            return (tudo[1] + ": " + tudo[2]);
        } else if (tudo[0].equals("NEWNICK")) {
            return (tudo[1] + " mudou de nome para " + tudo[2]);
        }
        if (tudo[0].equals("PRIVATE")) {
            return ("(Privado) " + tudo[1] + ": " + tudo[2]);
        }

        return message;
    }

    public void readInput() {
        try {
            String modifiedSentence = "";
            String afterTranslate = "";

            // System.out.println("Ready to read");
            while (modifiedSentence != null) {
                BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                modifiedSentence = inFromServer.readLine();
                afterTranslate = translate(modifiedSentence);

                System.out.println("From server: " + modifiedSentence);
                printMessage(afterTranslate + "\n");

                if (afterTranslate.equals("BYE")) {
                    try {
                        TimeUnit.SECONDS.sleep(2);
                    } catch (InterruptedException exception) {
                        System.out.println(exception);
                    }
                    System.exit(0);
                }

            }
            clientSocket.close();
        } catch (UnknownHostException exception) {
            System.out.println(exception);
        } catch (IOException exception) {
            System.out.println(exception);
        }
    }

    public void initialize() {
        try {
            ip = InetAddress.getByName(Gserver);
            clientSocket = new Socket(ip, Gport);
        } catch (UnknownHostException exception) {
            System.out.println(exception);
        } catch (IOException exception) {
            System.out.println(exception);
        }

    }

    // Método principal do objecto
    public void run() {

        initialize();

        clientThread R1 = new clientThread("Receive");
        R1.start();

        clientThread R2 = new clientThread("Send");
        R2.start();

    }

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
