import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
    static class Sala {
        String name;
        int sala_id;

        Sala() {
            this.name = "Geral"; // Importante: a sala "Geral" e uma sala "especial" que se entra no comeco da
                                 // execucao
                                 // nao se pode mandar mensagens ou entrar em outras salas dentro dessa "Geral"
            this.sala_id = 99999;
        }

        Sala(String name, int sala_id) {
            this.name = name;
            this.sala_id = sala_id;
        }
    }

    static class Client_info {
        // estrutura onde se guarda toda a info relacionada ao cliente

        int id;
        String address;
        String nickname;
        SelectionKey key;
        Selector selector;
        Sala sala;
        String mybuffer;
        boolean needBuffer;

        Client_info(int id, String address, SelectionKey key, Selector selector) {
            this.id = id;
            this.address = address;
            this.key = key;
            this.selector = selector;
            this.nickname = "";
            this.sala = new Sala();
            this.mybuffer = "";
            this.needBuffer = false;

        }

        void changeNick(String nickname) {
            this.nickname = nickname;
        }

        void changeSala(String nome_sala) {
            this.sala = new Sala(nome_sala, salaCount);
        }

    }

    private static void verifyAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();
        String address = sc.socket().getInetAddress().toString() + (":") + (sc.socket().getPort());
        sc.configureBlocking(false);
        Client_info curClient = new Client_info(clientCount, address, key, selector);
        clientCount++;

        sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, curClient);
        System.out.println("accepted connection from: " + address);
    }

    private static void verifyInput(SelectionKey key, Selector selector, ServerSocket ss) throws Exception {
        SocketChannel sc = (SocketChannel) key.channel();
        boolean ok = processInput(sc, selector, ss, key);

        // If the connection is dead, remove it from the selector
        // and close it
        if (!ok) {
            Client_info aux = (Client_info) key.attachment();
            nick_total[aux.id - 1] = "";
            try {
                printElse("LEFT " + aux.nickname + "\n", aux.selector, aux.sala, key);
            } catch (IOException ie2) {
                System.out.println(ie2);
            }
            key.cancel();
            Socket s = null;
            try {
                s = sc.socket();
                System.out.println("Closing connection to " + s);
                s.close();
            } catch (IOException ie) {
                System.err.println("Error closing socket " + s + ": " + ie);
            }
        }
    }

    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();
    static int clientCount;
    static int salaCount;
    static Sala sala_total[]; // todas as salas criadas ate agora
    static String nick_total[]; // todos os nicks criados ate agora
    static boolean printFirst;
    static boolean needBuffer;

    static void initializeNick() {
        for (int i = 0; i < 1000; i++) {
            nick_total[i] = "";
        }
    }

    static public void main(String args[]) throws Exception {
        sala_total = new Sala[1000];
        nick_total = new String[1000];
        initializeNick();

        salaCount = 0;

        // Parse port from command line
        int port = Integer.parseInt(args[0]);
        clientCount = 1;
        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel ssc = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            ssc.configureBlocking(false);

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);

            // Create a new Selector for selecting
            Selector selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            ssc.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Listening on port " + port);

            while (true) {
                // See if we've had any activity -- either an incoming connection,
                // or incoming data on an existing connection
                int num = selector.select();

                // If we don't have any activity, loop around and wait again
                if (num == 0) {
                    continue;
                }

                // Get the keys corresponding to the activity that has been
                // detected, and process them one by one
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    // Get a key representing one of bits of I/O activity
                    SelectionKey key = it.next();

                    // What kind of activity is it?
                    if (key.isAcceptable()) {

                        verifyAccept(key, selector);

                    } else if (key.isReadable()) {

                        try {
                            verifyInput(key, selector, ss);

                        } catch (IOException ie) {
                            SocketChannel sc = (SocketChannel) key.channel();
                            // On exception, remove this channel from the selector

                            key.cancel();

                            try {

                                sc.close();
                            } catch (IOException ie2) {
                                System.out.println(ie2);
                            }

                            System.out.println("Closed " + sc);
                        }
                    }
                }

                // We remove the selected keys, because we've dealt with them.
                keys.clear();
            }
        } catch (IOException ie) {
            System.err.println(ie);
        }
    }

    // imprimir para todos
    static private void printAll(String msg, Selector selector, Sala sala, SelectionKey myKey) throws IOException {
        ByteBuffer msgBuf = ByteBuffer.wrap(msg.getBytes());

        for (SelectionKey key : selector.keys()) {
            Client_info aux = (Client_info) key.attachment();
            if (key.isValid() && key.channel() instanceof SocketChannel && (aux.sala.name.equals(sala.name))) {
                SocketChannel sch = (SocketChannel) key.channel();
                sch.write(msgBuf);
                msgBuf.rewind();
            }
        }
    }

    // imprimir so para quem envia
    static private void printSolo(String msg, SelectionKey myKey) throws IOException {

        ByteBuffer msgBuf = ByteBuffer.wrap(msg.getBytes());

        if (myKey.isValid() && myKey.channel() instanceof SocketChannel) {

            SocketChannel sch = (SocketChannel) myKey.channel();
            sch.write(msgBuf);
            msgBuf.rewind();
        }
    }

    // imprimir para todos menos quem envia
    static private void printElse(String msg, Selector selector, Sala sala, SelectionKey myKey) throws IOException {
        ByteBuffer msgBuf = ByteBuffer.wrap(msg.getBytes());
        for (SelectionKey key : selector.keys()) {
            Client_info aux = (Client_info) key.attachment();
            if (key.isValid() && key.channel() instanceof SocketChannel && (aux.sala.name.equals(sala.name))
                    && key != myKey) {
                SocketChannel sch = (SocketChannel) key.channel();
                sch.write(msgBuf);
                msgBuf.rewind();
            }
        }
    }

    // imprimir para uma pessoa
    static private Boolean printPm(String msg, Selector selector, String dest, SelectionKey myKey) throws IOException {
        ByteBuffer msgBuf = ByteBuffer.wrap(msg.getBytes());
        for (SelectionKey key : selector.keys()) {
            Client_info aux = (Client_info) key.attachment();
            if (key.isValid() && key.channel() instanceof SocketChannel && aux.nickname.equals(dest)) {
                SocketChannel sch = (SocketChannel) key.channel();
                sch.write(msgBuf);
                msgBuf.rewind();
                return true;
            }
        }
        return false;
    }

    static boolean checkNick(String nick) { 
        for (int i = 0; i < 1000; i++) {
            if (nick_total[i].trim().equals(nick)) {
                return false;
            }
        }
        return true;
    }

    static private boolean checkCommand(String msg, SelectionKey key) {

        String tudo[];
        tudo = msg.split(" ", 2);

        if (tudo[0].trim().equals("/nick")) {
            Client_info aux = (Client_info) key.attachment();
            if (tudo.length == 2 && tudo[1].trim().length() != 0) {

                if (checkNick(tudo[1].trim())) {
                    String oldnick = aux.nickname;
                    nick_total[aux.id - 1] = tudo[1].trim();
                    aux.changeNick(tudo[1].trim());
                    key.attach(aux);

                    try {
                        printSolo("OK\n", key);
                        if (!aux.sala.name.equals("Geral"))
                            printElse("NEWNICK " + oldnick + " " + tudo[1] + "\n", aux.selector, aux.sala, key);
                    } catch (IOException ie2) {
                        System.out.println(ie2);
                    }

                } else {
                    // nick em uso
                    try {
                        printSolo("ERROR\n", key);
                    } catch (IOException ie2) {
                        System.out.println(ie2);
                    }
                }
            } else {
                try {
                    printSolo("ERROR\n", key);
                } catch (IOException ie2) {
                    System.out.println(ie2);
                }
            }
        }

        else if (tudo[0].trim().equals("/bye")) {
            Client_info aux = (Client_info) key.attachment();
            SocketChannel sc = (SocketChannel) key.channel();
            Socket s = sc.socket();
            nick_total[aux.id - 1] = "";
            try {
                printSolo("BYE\n", key);
                if (!aux.sala.name.equals("Geral"))
                    printElse("LEFT " + aux.nickname + "\n", aux.selector, aux.sala, key);
            } catch (IOException ie2) {
                System.out.println(ie2);
            }

            key.cancel();
            try {
                s.close();
            } catch (IOException ie2) {
                System.out.println(ie2);
            }
            System.out.println("Closing connection to " + s);
        }

        else if (tudo[0].trim().equals("/join")) {
            if (tudo.length == 2) {

                Client_info aux3 = (Client_info) key.attachment();

                if (aux3.nickname.equals("")) {
                    return false;

                }

                Boolean existe_sala = false;

                for (int i = 0; i < salaCount; i++) {
                    if (sala_total[i].name.equals(tudo[1].trim())) {
                        Client_info aux = (Client_info) key.attachment();
                        try {
                            if (!aux.sala.name.equals("Geral"))
                                printElse("LEFT " + aux.nickname + "\n", aux.selector, aux.sala, key);
                        } catch (IOException ie2) {
                            System.out.println(ie2);
                        }

                        aux.sala = new Sala(tudo[1].trim(), salaCount);
                        key.attach(aux);
                        existe_sala = true;
                        try {
                            printSolo("OK\n", key);
                            printElse("JOINED " + aux.nickname + "\n", aux.selector, aux.sala, key);
                        } catch (IOException ie2) {
                            System.out.println(ie2);
                        }

                    }

                }

                if (!existe_sala) {

                    Sala aux = new Sala(tudo[1].trim(), salaCount);
                    sala_total[salaCount] = aux;

                    Client_info aux2 = (Client_info) key.attachment();
                    aux2.sala = aux;
                    key.attach(aux2);
                    salaCount++;
                    try {
                        printSolo("OK\n", key);
                    } catch (IOException ie2) {
                        System.out.println(ie2);
                    }

                }
            } else {
                try {
                    printSolo("ERROR\n", key);
                } catch (IOException ie2) {
                    System.out.println(ie2);
                }
            }

        } else if (tudo[0].trim().equals("/leave")) {
            Client_info aux = (Client_info) key.attachment();
            if (!aux.sala.name.equals("Geral")) {

                try {
                    printSolo("OK\n", key);
                    if (!aux.sala.name.equals("Geral"))
                        printElse("LEFT " + aux.nickname + "\n", aux.selector, aux.sala, key);
                } catch (IOException ie2) {
                    System.out.println(ie2);
                }

                aux.sala = new Sala();
                key.attach(aux);
            } else {
                try {
                    printSolo("ERROR\n", key);
                } catch (IOException ie2) {
                    System.out.println(ie2);
                }

            }
        } else if (tudo[0].trim().equals("/priv")) {
            if (tudo.length == 2) {
                String tudo2[];
                tudo2 = tudo[1].split(" ", 2);

                if (tudo2.length != 2) {
                    try {
                        printSolo("ERROR\n", key);
                    } catch (IOException ie2) {
                        System.out.println(ie2);
                    }
                    return true;
                }

                Client_info aux = (Client_info) key.attachment();
                try {
                    String myName = aux.nickname.trim();

                    if (!aux.sala.name.equals("Geral"))
                        if (printPm("PRIVATE " + myName.trim() + " " + tudo2[1].trim() + "\n", aux.selector, tudo2[0],
                                key)) {
                            printSolo("OK\n", key);
                        } else
                            printSolo("ERROR\n", key);
                } catch (IOException ie2) {
                    System.out.println(ie2);
                }

            } else {
                try {
                    printSolo("ERROR\n", key);
                } catch (IOException ie2) {
                    System.out.println(ie2);
                }
            }
            return true;
        } else if (tudo[0].trim().charAt(0) == '/') {
            if (tudo[0].length() == 1) {
                try {
                    printSolo("ERROR\n", key);
                } catch (IOException ie2) {
                    System.out.println(ie2);
                }
                return true;
            } else if (tudo[0].charAt(1) == '/') {

                printFirst = true;
                return false;
            } else {
                try {
                    printSolo("ERROR\n", key);
                } catch (IOException ie2) {
                    System.out.println(ie2);
                }

                return true;
            }

        }

        else {

            return false;
        }
        return true;
    }

    static private boolean processInput(SocketChannel sc, Selector selector, ServerSocket ss, SelectionKey key)
            throws IOException {

        Boolean printThis = true;

        String message = "";

        int read = 0;

        while ((read = sc.read(buffer)) > 0) {
            buffer.flip();

            printFirst = false;

            String allinput = decoder.decode(buffer).toString();

            Client_info curCli = (Client_info) key.attachment();
            if (curCli.needBuffer) {
                allinput = curCli.mybuffer + allinput;
                needBuffer = false;
                curCli.mybuffer = "";
            }
            // verificar se uma mensagem foi mandada sem um END OF TRANSMISSION
            if (!allinput.endsWith("\n") && allinput.split("\r\n").length == 1) {
                curCli.mybuffer = allinput;
                curCli.needBuffer = true;

                allinput = "";
            }

            // fazemos isso para verificar o codigo linha em linha
            for (String mess : allinput.split("\r\n")) {
                printThis = true;
                message = mess + "\n";

                printFirst = false;

                if (message.trim().length() == 0)
                    printThis = false; // nao queremos processar uma msg vazia...
                else if (mess.charAt(0) == '/' && checkCommand(message, key))
                    printThis = false;
                if (printFirst) {
                    message = message.replaceFirst("/", "");

                }

                buffer.clear();

                Client_info aux;
                String finalMessage = "";
                aux = (Client_info) key.attachment();

                if (read < 0) {
                    return false;
                } else {

                    finalMessage += aux.nickname + " " + message;
                }

                if (printThis) {

                    System.out.println("From user: " + finalMessage);
                    try {

                        if (aux.sala.name.trim().equals("Geral"))
                            printSolo("ERROR\n", key);
                        else
                            printAll("MESSAGE " + finalMessage, selector, aux.sala, aux.key);
                    } catch (IOException ie2) {
                        System.out.println(ie2);
                        return false;

                    }
                }

            }
            return true;

        }
        return false;
    }

}