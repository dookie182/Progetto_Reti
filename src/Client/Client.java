package Client;

import Exceptions.AlreadyExistingAccountException;
import Server.NotifyInterface;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Client {

    private SocketChannel sc;
    private SocketAddress address;
    private String multicastAdrress = new String();
    private InetAddress ia;
    private MulticastSocket ms;
    private String editingDoc;
    private String user;
    private ConcurrentLinkedQueue queue;
    private Chat myChat;
    private boolean isEditing;
    private RemoteInterface serverObject;
    private Remote RemoteObject;
    private Registry r;
    private NotifyInterface stub;
    private String editor;

    public Client(String editor,String port){
            this.address = new InetSocketAddress(Integer.parseInt(port));
            this.editor = editor;
    }
    /**
     * Funzione che gestisce l'interazione con l'utente tramite la console.
     */
    public void start(){

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            r = LocateRegistry.getRegistry(50236);
            RemoteObject = r.lookup("SERVER");
            serverObject = (RemoteInterface) RemoteObject;
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }


        while(true){
            System.out.println("Benvenuto su TURING");
            System.out.println("--> register  <Username> <Password> : Registra un nuovo Utente \n" +
                    "--> login <Username> <Password> : Effettua il Login \n");

            init:
            try {
                String l = br.readLine();
                String[] input;
                input = l.split(" ");

                if (input.length != 3) {
                    System.out.println("Inserire correttamente i parametri");
                    break init;
                }

                    switch (input[0]) {
                        case "register":
                            register(input[1], input[2]);
                            break init;

                        case "login":
                            if (login(input[1], input[2])) {
                                break;
                            } else break init;

                        default:
                            System.out.println("Inserire una scelta valida");
                            break init;

                    }

                op:{
                    while (true) {
                        System.out.println("Scegli un'operazione disponibile\n" +
                                "1. create <Doc> <Num_Sezioni>: Crea un nuovo documento \n" +
                                "2. share <Doc> <Username>: Invita utente alla modifica \n" +
                                "3. list: Elenca tutti i documenti dell'utente corrente\n" +
                                "4. show: <Doc> <Num_Sezione>: Visualizza la sezione scelta del documento\n" +
                                "5. edit: <Doc> <Num_Sezione>: Permette di editare la sezione scelta del documento\n" +
                                "6. logout: Effettua il Logout \n");
                        l = br.readLine();
                        input = l.split(" ");

                        switch (input[0]) {

                            case "create":
                                if(input.length != 3){
                                    System.out.println("Inserire correttamente i parametri!");
                                    break;
                                }
                                createDocument(input[1], input[2]);
                                break;

                            case "share":
                                if(input.length != 3){
                                    System.out.println("Inserire correttamente i parametri!");
                                    break;
                                }
                                share(input[1], input[2]);
                                break;

                            case "show":
                                if(input.length == 2)
                                    show(input[1],"");
                                else {
                                    show(input[1], input[2]);
                                }
                                break;

                            case "list":
                                showList();
                                break;

                            case "edit":
                                if(input.length != 3){
                                    System.out.println("Inserire correttamente i parametri!");
                                    break;
                                }
                                isEditing = edit(input[1],input[2]);

                                while(isEditing) {
                                    System.out.println("Scegli un'operazione disponibile\n" +
                                            "1. end-edit: <Doc> <Num_Sezione>: Chiude la sessione di editing del documento\n"+
                                            "2. send: <Messagge>: Invia un messaggio sulla chat del documento\n"+
                                            "3. receive: Visualizza tutti i messaggi non letti nella chat del documento\n");
                                    l = br.readLine();
                                    input = l.split(" ");

                                    switch (input[0]) {
                                        case "end-edit":
                                            if(input.length != 3){
                                                System.out.println("Inserire correttamente i parametri!");
                                                break;
                                            }
                                            endEdit(input[1], input[2]);
                                            break;

                                        case "receive":
                                            receive();
                                            break;

                                        case "send":
                                            System.out.println("Messaggio Inviato: "+l.substring(5));
                                            send(l.substring(5));
                                            break;

                                        default:
                                            System.out.println("INSERIMENTO ERRATO: Scegliere un'operazione valida!");
                                            break;
                                    }
                                }
                                break;

                            case "logout":
                                logout();
                                break op;

                            default:
                                System.out.println("INSERIMENTO ERRATO: Scegliere un'operazione valida!");
                                break;
                        }
                    }
                }
            } catch (IOException e) {
               e.printStackTrace();
            }

        }
    }

    public void register(String username, String password){

        try {
            serverObject.register(username,password);
            System.out.println("Registrazione avvenuta con successo! Effettua il Login per iniziare.");

        } catch (RemoteException e) {
            System.out.println("Errore di registrazione");
        } catch (AlreadyExistingAccountException e) {
            System.out.println(e.getMessage());
        }
    }

    public boolean login(String username, String password){
        String data = "login" +" "+ username +" "+ password;
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

        try {
            sc = SocketChannel.open();
            sc.connect(address);
            byteBuffer.clear();
            byteBuffer.put(data.getBytes());
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) {
                sc.write(byteBuffer);
            }
            byteBuffer.clear();

            String tmp = "";
            sc.read(byteBuffer);
            byteBuffer.flip();

            while(byteBuffer.hasRemaining()){
                tmp += (char)byteBuffer.get();
            }

            if(tmp.substring(0,7).equals("success")) {
                System.out.println("Login effettuato con successo!");
                System.out.println(tmp.substring(8));
                user = username;
                NotifyInterface callBackOBJ = new MyNotify();
                stub = (NotifyInterface) UnicastRemoteObject.exportObject(callBackOBJ,0);
                serverObject.registerCallBack(username,stub);
                return true;
            }
            else System.out.println(tmp);
        } catch (IOException e) {
            System.out.println("Errore di Connessione: riavvia il Client!");
        }
        return false;
    }

    public void logout(){

        String data = "logout";

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        try {
            byteBuffer.clear();
            byteBuffer.put(data.getBytes());
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) {
                sc.write(byteBuffer);
            }
            byteBuffer.clear();

            String tmp = "";
            sc.read(byteBuffer);
            byteBuffer.flip();

            while(byteBuffer.hasRemaining()){
                tmp += (char)byteBuffer.get();
            }
            System.out.println(tmp);
            serverObject.unregisterCallBack(user);
            sc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createDocument (String name, String numSection){
        String data = "create" +" "+ name +" "+ numSection;

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        try {

            byteBuffer.clear();
            byteBuffer.put(data.getBytes());
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) {
                sc.write(byteBuffer);
            }
            byteBuffer.clear();

            String tmp = "";
            sc.read(byteBuffer);
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()){
                tmp += (char)byteBuffer.get();
            }
            System.out.println(tmp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void share(String doc, String utente ){
        String data = "share "+ utente +" "+ doc;
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

        try {
            byteBuffer.clear();
            byteBuffer.put(data.getBytes());
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) {
                sc.write(byteBuffer);
            }
            byteBuffer.clear();

            String tmp = "";
            sc.read(byteBuffer);
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()){
                tmp += (char)byteBuffer.get();
            }
            System.out.println(tmp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showList(){

        String data = "list";

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        try {
            byteBuffer.clear();
            byteBuffer.put(data.getBytes());
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) {
                sc.write(byteBuffer);
            }
            byteBuffer.clear();

            String tmp = "";
            sc.read(byteBuffer);
            byteBuffer.flip();

            while (byteBuffer.hasRemaining()){
                tmp += (char)byteBuffer.get();
            }

            int bufferSize = Integer.parseInt(tmp);

            if(bufferSize > 0) {
                byteBuffer = ByteBuffer.allocate(bufferSize);
                tmp = "";
                byteBuffer.clear();

                sc.read(byteBuffer);
                byteBuffer.flip();

                System.out.println("Lista dei Documenti\n");
                while (byteBuffer.hasRemaining()) {
                    tmp += (char) byteBuffer.get();
                }
                System.out.println(tmp);
            }else
                System.out.println("Nessun Documento da visualizzare!\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean show(String doc,String section){
        String data;
        if(section.isEmpty()) {
            data = "show" + " " + doc;
        }else{
            data = "show" + " " + doc +" "+ section;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        try {
            byteBuffer.clear();
            byteBuffer.put(data.getBytes());
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) {
                sc.write(byteBuffer);
            }
            byteBuffer.clear();

            String tmp = new String();
            sc.read(byteBuffer);
            byteBuffer.flip();

            while(byteBuffer.hasRemaining())
                tmp += (char)byteBuffer.get();

            if(tmp.substring(0,2).equals("-1")) {
                System.out.println(tmp.substring(2));
                return false;
            }

            long size = Integer.parseInt(tmp.substring(2));

            byteBuffer.clear();

            int readBytes = 0;

            while(readBytes != size){
                tmp = "";
                readBytes += sc.read(byteBuffer);
                byteBuffer.flip();
                while(byteBuffer.hasRemaining()){
                    tmp += (char)byteBuffer.get();
                }
                byteBuffer.clear();
                System.out.println(tmp);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean edit(String doc, String section){
        String data = "edit" +" "+ doc +" "+ section;
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

        try {
            byteBuffer.clear();
            byteBuffer.put(data.getBytes());
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) {
                sc.write(byteBuffer);
            }
            byteBuffer.clear();

            sc.read(byteBuffer);
            byteBuffer.flip();

            String tmp = new String();

            while(byteBuffer.hasRemaining())
                tmp+=(char)byteBuffer.get();

            if(tmp.substring(0,2).equals("-1")){
                System.out.println(tmp.substring(2));
                return false;
            }

            int size = Integer.parseInt(tmp.substring(2));

            String path = Paths.get("scaricati/"+doc+"/"+section+".txt").toString();

            File document = new File("scaricati/"+doc);
            document.mkdirs();

            FileChannel fc = FileChannel.open(Paths.get("scaricati/"+doc+"/"+section+".txt"), StandardOpenOption.CREATE,StandardOpenOption.WRITE, StandardOpenOption.READ);

            int curr = 0;
            while(curr != size) {
               long transferred = fc.transferFrom(sc, curr, size);
                curr += transferred;
            }

            Runtime.getRuntime().exec(editor+" "+path);
            System.out.println("Documento scaricato con successo!");

            byteBuffer.clear();
            sc.read(byteBuffer);
            byteBuffer.flip();
            while(byteBuffer.hasRemaining())
                multicastAdrress += (char) byteBuffer.get();

            queue = new ConcurrentLinkedQueue();
            ms = new MulticastSocket(4400);
            ia = InetAddress.getByName(multicastAdrress);
            myChat = new Chat(user,ia,ms,queue);

            //Avvio la chat del documento;
            Thread t = new Thread(myChat);
            t.start();
            System.out.println("Sei stato connesso alla chat del documento "+doc);

            editingDoc = doc;
            fc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public void endEdit(String doc, String section){
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

        try {

            String path = Paths.get("scaricati/"+doc+"/"+section+".txt").toString();

            FileChannel in = FileChannel.open(Paths.get(path), StandardOpenOption.READ,StandardOpenOption.DELETE_ON_CLOSE);

            String data = "endedit" +" "+ doc +" "+ section + " "+ in.size();
            byteBuffer.clear();
            byteBuffer.put(data.getBytes());
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) {
                sc.write(byteBuffer);
            }
            byteBuffer.clear();

            //Attendo una conferma di ricezione dei byte da scrivere da parte del Server.Server;
            sc.read(byteBuffer);

            byteBuffer.clear();

            //Chiudo la chat del documento;
            myChat.shutDown();
            queue.clear();
            multicastAdrress = "";

            while(in.position()!= in.size()) {
                long transferred = in.transferTo(in.position(), in.size(), sc);
                in.position(in.position()+transferred);
            }
            in.close();
            isEditing = false;

            String tmp = new String();
            sc.read(byteBuffer);
            byteBuffer.flip();

            for(int i = 0; i < byteBuffer.limit();i++){
                tmp += (char)byteBuffer.get(i);
            }
            System.out.println(tmp);
        } catch (IOException e) {
            System.out.println("Documento non scaricato! Termina la modifica della sezione corrente");
        }
    }

    public void send(String message){

        Calendar cal =  Calendar.getInstance(TimeZone.getDefault());
        int ora = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);

        String m = "["+ora+":"+min+"-"+user+"] "+message;
        DatagramPacket msg = new DatagramPacket(m.getBytes(StandardCharsets.UTF_8), 0,
                m.getBytes(StandardCharsets.UTF_8).length,ia,4400);
        try {
            ms.send(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void receive(){

        if(queue.isEmpty())
            System.out.println("Nessun nuovo messaggio!");
        else {

            while(!queue.isEmpty()){
                System.out.println(queue.poll());
            }
        }
    }



    public static void main (String args[]){
        Client TuringClient = new Client(args[0],args[1]);

        TuringClient.start();

    }
}
