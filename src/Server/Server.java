package Server;

import Client.RemoteInterface;
import Exceptions.*;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.AccessException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class Server {


    private MyRemoteServer statsService = new MyRemoteServer();
    private ObjectMapper mapper = new ObjectMapper();
    private String docPath = "DocDB.json";
    private String userPath = "UserDB.json";
    private Vector<Document> docDB;
    private HashMap<String,Integer> clientMap;
    private int ipCounter = 5;
    private HashMap<String, String> chatMap;
    private Selector selector;
    private String getByPort(Integer port){
        for (Map.Entry<String,Integer> entry: clientMap.entrySet()) {
            if(entry.getValue().intValue() == port.intValue())
                return entry.getKey();
        }

        return "-1";
    }

    public Server (String serverPort) {
        try {
            mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            docDB = new Vector<>();

            clientMap = new HashMap<>();
            chatMap = new HashMap<>();

            //Deserializzo la struttura dati dei documenti;
            try {
                docDB = mapper.readValue(new File(docPath), new TypeReference<Vector<Document>>() {
                });
            } catch (IOException e) {
                System.out.println("Database documenti vuoto: nessuna struttura da deserializzare");
            }

            RemoteInterface stub = (RemoteInterface) UnicastRemoteObject.exportObject(statsService, 50236);

            LocateRegistry.createRegistry(50236);

            Registry r = LocateRegistry.getRegistry(50236);

            r.rebind("SERVER", (Remote) stub);

            System.out.println("Server ready");

            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(Integer.parseInt(serverPort)));
            serverSocketChannel.configureBlocking(false);
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        } catch (AccessException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        while (true) {

            selector.selectedKeys().clear();
            try {
                selector.select();
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (SelectionKey key : selector.selectedKeys()) {

                try {

                    if (key.isAcceptable()) {
                        //Verifico che il canale registrato alla chiave key sia in stato ACCEPTABLE;
                        //Accetto la connessione e passo all'operazione successiva;

                        SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
                        client.configureBlocking(false);


                        //Cambio la SelectionKey associata al canale per passare all'operazione successiva;
                        client.register(selector, SelectionKey.OP_READ);
                    }

                    if (key.isReadable()) {
                        //Verifico che il canale registrato alla chiave key sia in stato READABLE;

                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        buffer.clear();
                        String op = "";

                        while (client.read(buffer) != 0) {
                            buffer.flip();
                            while (buffer.hasRemaining()) {
                                op += (char) buffer.get();
                            }
                            buffer.clear();
                        }

                        String[] input;
                        input = op.split(" ");

                        //Cambio la SelectionKey associata al canale per passare all'operazione
                        //successiva (Lettura e scrittura del File sul SocketChannel);
                        key.channel().register(selector, SelectionKey.OP_WRITE, input);
                    }

                    if (key.isWritable()) {
                        //Verifico che il canale registrato alla chiave key sia in stato WRITABLE;
                        //Reperisco il File dal Server.Server e lo Scrivo sul SocketChannel associato alla chiave key;
                        SocketChannel client = (SocketChannel) key.channel();

                        String[] op = (String[]) key.attachment();
                        switch (op[0]) {

                            case "login":
                                int index = statsService.indexOf(op[1]);
                                int port = client.socket().getPort();
                                String current = getByPort(port);
                                ByteBuffer buffer = ByteBuffer.allocate(1024);
                                buffer.clear();

                                try {
                                    if (index < 0) throw new MissingAccountException();

                                    //Controllo che l'utente non sia già Online;
                                    if (clientMap.containsKey(op[1])) {
                                        throw new OnlineException();
                                    }

                                    //Verifico le credenziali utente inserite;
                                    if (!statsService.users.get(index).checkIdentity(op[2])) {
                                        throw new WrongIdentityException();
                                    }

                                    clientMap.putIfAbsent(op[1], port);

                                    String tmp = "success ";

                                    String msg;

                                    while((msg = statsService.users.get(index).getMsg()) != null){
                                        tmp += msg+"\n";
                                    }

                                    buffer.clear();
                                    buffer.put(tmp.getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();

                                } catch (WrongIdentityException | OnlineException | MissingAccountException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                }
                                key.channel().register(selector, SelectionKey.OP_READ);
                                break;

                            case "logout":
                                buffer = ByteBuffer.allocate(1024);
                                buffer.clear();
                                port = client.socket().getPort();
                                current = getByPort(port);

                                //Controllo che l'utente non sia già Offline;
                                try {
                                    if (!clientMap.containsValue(port)) {
                                        throw new OfflineException();
                                    }

                                    //Rimuovo il Client dalla HashMap dei Client connessi;
                                    clientMap.remove(current);

                                    //Serializzo la struttura dati degli utenti;
                                    mapper.writeValue(new File(userPath),statsService.users);

                                    String tmp = "Logout effettuato con successo!";

                                    buffer.clear();
                                    buffer.put(tmp.getBytes());
                                    buffer.flip();

                                    //Invio l'esito dell'operazione al Client;
                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                } catch (OfflineException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                }

                                key.cancel();

                                break;

                            case "create":
                                buffer = ByteBuffer.allocate(1024);
                                port = client.socket().getPort();
                                current = getByPort(port);
                                index = statsService.indexOf(current);
                                try {
                                    //Verifico che il numero delle sezioni sia maggiore di 0;
                                    if(Integer.parseInt(op[2]) <= 0) throw new FormatException("Inserire un numero di sezioni maggiore di 0");
                                    Document doc = new Document(current, op[2], op[1]);
                                    doc.create();
                                    docDB.add(doc);
                                    statsService.users.get(index).addDocument(op[1]);

                                    //Serializzo la struttura dati dei documenti;
                                    mapper.writeValue(new File(docPath), docDB);

                                    //Serializzo la struttura dati degli utenti;
                                    mapper.writeValue(new File(userPath), statsService.users);
                                    String tmp = "Documento creato con successo!";

                                    buffer.clear();
                                    buffer.put(tmp.getBytes());
                                    buffer.flip();

                                    //Invio l'esito dell'operazione al Client;
                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();

                                } catch(NumberFormatException e){
                                    buffer.put("Inserire il numero delle sezioni in formato numerico!".getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();

                                } catch (ExistingDocumentException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                }catch(FormatException e){
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                }

                                key.channel().register(selector, SelectionKey.OP_READ);
                                break;

                            case "share":
                                buffer = ByteBuffer.allocate(1024);
                                index = indexOfDoc(op[2]);
                                int user = statsService.indexOf(op[1]);
                                port = client.socket().getPort();
                                current = getByPort(port);
                                try {
                                    //Controllo che l'utente con cui sto cercando di condividere il documento non sia l'utente corrente;
                                    if(user == statsService.indexOf(current)) throw new FormatException("Stai cercando di condividere il documento con te stesso!");

                                    //Controllo che esista il documento da condividere;
                                    if (index < 0) throw new MissingDocumentException();

                                    //Controllo che esista l'utente con cui condividere il documento;
                                    if (user < 0) throw new MissingAccountException();

                                    //Controllo che l'utente corrente abbia i permessi per condividere il documento;
                                    if (!docDB.get(index).getCreator().equals(current))
                                        throw new NotAllowedSharingException();

                                    docDB.get(index).addAllowedUser(op[1]);
                                    statsService.users.get(user).addDocument(op[2]);

                                    //Serializzo la struttura dati dei documenti;
                                    mapper.writeValue(new File(docPath), docDB);

                                    //Serializzo la struttura dati degli utenti;
                                    mapper.writeValue(new File(userPath),statsService.users );

                                    String tmp = "Documento condiviso con successo all'utente " + op[1] + "!";

                                    buffer.clear();
                                    buffer.put(tmp.getBytes());
                                    buffer.flip();

                                    //Invio la notifica di avvenuta condivisione al Client;
                                    statsService.doCallBack(current,op[1],op[2]);

                                    //Invio l'esito dell'operazione;
                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                } catch (MissingDocumentException | AlreadyInvitedUserException | MissingAccountException | NotAllowedSharingException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                } catch (FormatException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                }
                                key.channel().register(selector, SelectionKey.OP_READ);
                                break;

                            case "list":
                                buffer = ByteBuffer.allocate(1024);
                                port = client.socket().getPort();
                                current = getByPort(port);

                                index = statsService.indexOf(current);

                                //Recupero la lista dei documenti creati o condivisi con l'utente corrente;
                                Vector<String> userList = statsService.users.get(index).getList();

                                String tmp = new String();

                                for (String doc:userList) {
                                    tmp += "Nome Documento: "+doc+"\n"+
                                            "Numero Sezioni: "+docDB.get(indexOfDoc(doc)).getNumSezioni()+"\n"+
                                            "Creatore: "+docDB.get(indexOfDoc(doc)).getCreator()+"\n"+
                                            "Collaboratori: "+docDB.get(indexOfDoc(doc)).getAllowedUsers()+"\n\n";
                                }

                                //Invio la dimensione della stringa che contiene la lista dei documenti al Client;
                                buffer.clear();
                                buffer.put((""+tmp.getBytes().length).getBytes());
                                buffer.flip();
                                while (buffer.hasRemaining()){
                                    client.write(buffer);
                                }

                                buffer.clear();

                                System.out.println("Invio la lista dei documenti dell'utente "+ current);

                                //Invio la lista dei documenti creata al Client;
                                buffer.clear();
                                buffer.put(tmp.getBytes());
                                buffer.flip();
                                while (buffer.hasRemaining()) {
                                    client.write(buffer);
                                }
                                buffer.clear();

                                key.channel().register(selector, SelectionKey.OP_READ);
                                break;

                            case "show":
                                buffer = ByteBuffer.allocate(1024);
                                port = client.socket().getPort();
                                current = getByPort(port);

                                try {
                                    index = indexOfDoc(op[1]);

                                    //Controllo che il documento richiesto sia esistente;
                                    if (index < 0) throw new MissingDocumentException();
                                    int numSezioni = docDB.get(index).getNumSezioni();

                                    //Controllo che l'utente corrente abbia i permessi per visualizzare il documento;
                                    if(!docDB.get(index).getCreator().equals(current) && !docDB.get(index).checkAllowedUser(current)) throw new NotAuthorizedUserException();

                                    //Invio di una sezione specifica di un documento;
                                    if(op.length == 3) {
                                        System.out.println("Invio la sezione "+ op[2]+" del documento "+ op[1]);

                                        //Controllo che la sezione richiesta sia esistente;
                                        if(Integer.parseInt(op[2]) >= numSezioni) throw new MissingSectionException();
                                        String path = Paths.get(op[1] + "/" + op[2] + ".txt").toString();
                                        FileChannel in = FileChannel.open(Paths.get(op[1] + "/" + op[2] + ".txt"), StandardOpenOption.READ);

                                        //Invio il numero di byte da leggere al Client;
                                        String size = "ok"+in.size();

                                        buffer.put(size.getBytes());
                                        buffer.flip();
                                        while (buffer.hasRemaining()) {
                                            client.write(buffer);
                                        }
                                        buffer.clear();

                                        //Invio la sezione richiesta al Client;
                                        while (in.position() != in.size()) {
                                            long transferred = in.transferTo(in.position(), in.size(), client);
                                            in.position(transferred + in.position());
                                        }
                                    }

                                    //Invio di tutte le sezioni di un documento;
                                    if(op.length == 2){
                                        buffer = ByteBuffer.allocate(1024);

                                        System.out.println("Invio il documento "+ op[1]);
                                        FileChannel total = FileChannel.open(Paths.get(op[1]+"/tmp.txt"),StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ,StandardOpenOption.DELETE_ON_CLOSE);
                                        for(int i = 0; i < numSezioni;i++) {
                                            FileChannel in = FileChannel.open(Paths.get(op[1] + "/" + i + ".txt"),StandardOpenOption.READ);
                                            if (docDB.get(index).isEditing(i))
                                                buffer.put(("\n\nSezione "+i+" in modifica\n\n").getBytes());
                                            else
                                                buffer.put(("\n\nSezione "+i+" non in modifica\n\n").getBytes());

                                            buffer.flip();

                                            while(buffer.hasRemaining())
                                                total.write(buffer);

                                            buffer.clear();

                                            //Invio il documento richiesto al Client;
                                            while (in.position() != in.size()) {
                                                long transferred = in.transferTo(in.position(), in.size(), total);
                                                in.position(transferred + in.position());
                                            }
                                        }

                                        String size ="ok"+total.size();
                                        buffer.put(size.getBytes());
                                        buffer.flip();

                                        //Invio l'esito dell'operazione al Client;
                                        while (buffer.hasRemaining()) {
                                            client.write(buffer);
                                        }

                                        buffer.clear();

                                        total.position(0);
                                        while (total.position() != total.size())
                                        {
                                            long transferred = total.transferTo(total.position(), total.size(), client);
                                            total.position(transferred + total.position());
                                        }
                                        total.close();
                                    }


                                } catch (MissingDocumentException e) {
                                    buffer.put(("-1"+e.getMessage()).getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                } catch (NotAuthorizedUserException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                } catch(NumberFormatException e){
                                    buffer.put("-1Inserire il numero delle sezioni in formato numerico!".getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();

                                } catch (MissingSectionException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                }
                                key.channel().register(selector, SelectionKey.OP_READ);
                                break;

                            case "edit":
                                buffer = ByteBuffer.allocate(1024);

                                try {
                                    index = indexOfDoc(op[1]);
                                    port = client.socket().getPort();
                                    current = getByPort(port);
                                    int userIndex = statsService.indexOf(current);

                                    //Controllo che il documento richiesto sia esistente;
                                    if (index < 0) throw new MissingDocumentException();

                                    int numSezioni = docDB.get(index).getNumSezioni();

                                    //Controllo che l'utente corrente abbia i permessi per visualizzare il documento;
                                    if(!docDB.get(index).getCreator().equals(current) && !docDB.get(index).checkAllowedUser(current)) throw new NotAuthorizedUserException();

                                    //Controllo che la sezione richiesta sia esistente;
                                    if(Integer.parseInt(op[2]) >= numSezioni) throw new MissingSectionException();

                                    //Imposto la sezione come in modifica;
                                    docDB.get(index).setIsEditing(op[2]);

                                    //Salvo l'indice dell'ultimo documento e dell'ultima sezione aperta in modifica;
                                    statsService.users.get(userIndex).setLastDocIndex(index);
                                    statsService.users.get(userIndex).setLastSectionIndex(op[2]);

                                    FileChannel in = FileChannel.open(Paths.get(op[1] + "/" + op[2] + ".txt"), StandardOpenOption.READ);

                                    //Invio l'esito dell'operazione al Client;
                                    String size = "ok" + in.size();
                                    buffer.put(size.getBytes());
                                    buffer.flip();
                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }

                                    buffer.clear();

                                    //Invio la sezione richiesta al Client;
                                    while (in.position() != in.size()) {
                                        long transferred = in.transferTo(in.position(), in.size(), client);
                                        in.position(in.position() + transferred);

                                    }

                                    System.out.println("Documento: "+op[1]+" Sezione: "+op[2]+" inviata con successo!");
                                    in.close();

                                    //Genero un indirizzo Multicast per la chat del documento se non è già presente nella chatMap;
                                    if (!chatMap.containsKey(op[1])) {
                                        chatMap.put(op[1], "227.0.0." + ipCounter);
                                        ipCounter++;
                                    }

                                    buffer.clear();

                                    //Invio l'indirizzo Multicast al Client;
                                    String indirizzo = chatMap.get(op[1]);
                                    buffer.put(indirizzo.getBytes());
                                    buffer.flip();
                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();

                                } catch (MissingDocumentException | EditingException e) {
                                    buffer.put(("-1"+e.getMessage()).getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                } catch (NotAuthorizedUserException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                }  catch(NumberFormatException e){
                                    buffer.put("-1Inserire il numero delle sezioni in formato numerico!".getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();

                                }  catch (MissingSectionException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                }

                                key.channel().register(selector, SelectionKey.OP_READ);
                                break;

                            case "endedit":

                                buffer = ByteBuffer.allocate(1024);
                                index = indexOfDoc(op[1]);
                                port = client.socket().getPort();
                                current = getByPort(port);
                                int userIndex = statsService.indexOf(current);

                                try {
                                    buffer.put("1".getBytes());
                                    buffer.flip();
                                    while(buffer.hasRemaining())
                                        client.write(buffer);

                                    docDB.get(index).endEditing(op[2]);

                                    //Resetto gli indici dell'ultimo documento e dell'ultima sezione aperta in modifica;
                                    statsService.users.get(userIndex).setLastDocIndex(-1);
                                    statsService.users.get(userIndex).setLastSectionIndex("-1");
                                    tmp = "Editing del documento: " + op[1] + " sezione: " + op[2] + " terminato con successo!";

                                    String path = Paths.get(op[1] + "/" + op[2] + ".txt").toString();

                                    long size = Integer.parseInt(op[3]);
                                    FileChannel fc = FileChannel.open(Paths.get(path), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);

                                    //Trasferisco il file sul Server;
                                    int curr = 0;
                                    while (curr != size) {
                                        long transferred = fc.transferFrom(client, curr, size);
                                        curr += transferred;
                                    }

                                    System.out.println(tmp);
                                    buffer.clear();
                                    buffer.put(tmp.getBytes());
                                    buffer.flip();

                                    //Invio l'esito al Client;
                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();


                                } catch (EditingException e) {
                                    buffer.put(e.getMessage().getBytes());
                                    buffer.flip();

                                    while (buffer.hasRemaining()) {
                                        client.write(buffer);
                                    }
                                    buffer.clear();
                                }

                                key.channel().register(selector, SelectionKey.OP_READ);
                                break;
                        }
                    }


                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (ClosedChannelException e) {
                    e.printStackTrace();
                } catch (JsonGenerationException e) {
                    e.printStackTrace();
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (JsonMappingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    System.out.println("Connessione Client chiusa forzatamente");
                    try {
                        //Gestisco la chiusura forzata di un Client;
                        SocketChannel client = (SocketChannel) key.channel();
                        int port = client.socket().getPort();
                        String current = getByPort(port);
                        int index = statsService.indexOf(current);

                        if(statsService.indexOf(current) >= 0) {
                           int lastDocIndex = statsService.users.get(index).getLastDocIndex();
                           if (lastDocIndex != -1) {
                               docDB.get(statsService.users.get(index).getLastDocIndex()).endEditing(statsService.users.get(index).getLastSectionIndex());
                           }
                           System.out.println("Sessione utente " + current + " chiusa correttamente!");
                           clientMap.remove(current);
                       }
                    } catch (EditingException e1) {
                        System.out.println("Nessun editing in corso!");
                    }
                    key.cancel();
                }
            }
        }
    }
    public static void main (String args[]){
        Server TuringServer = new Server(args[0]);


    }

    public int indexOfDoc(String docName){

        //Metodo utilizzato per restituire l'indice del Documento di nome "docName";
        for(int i = 0; i < docDB.size();i++){
            if(docDB.get(i).getName().equals(docName))
            return i;
        }

        return -1;
    }
}

