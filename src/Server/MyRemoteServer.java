package Server;

import Client.RemoteInterface;
import Exceptions.AlreadyExistingAccountException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.ArrayList;
import java.util.HashMap;

public class MyRemoteServer extends RemoteServer implements RemoteInterface {

    protected ArrayList<User> users;
    private HashMap<String,NotifyInterface> clientList;
    private ObjectMapper mapper;
    private String filename;

    public MyRemoteServer(){
        this.users = new ArrayList<>();
        this.mapper = new ObjectMapper();
        this.filename = "UserDB.json";
        this.clientList = new HashMap<>();

        //Deserializzo la struttura dati degli utenti;
        try {
            users = mapper.readValue(new File(filename), new TypeReference<ArrayList<User>>(){});
        } catch (IOException e) {
            System.out.println("Database utenti vuoto: nessuna struttura da deserializzare");
        }
    }

    public synchronized void register(String username, String password) throws AlreadyExistingAccountException {
        if (username == null || password == null) throw new NullPointerException();

        //Controllo che l'utente "username" non sia già registrato;
        if (indexOf(username) >= 0) throw new AlreadyExistingAccountException();

        User utente = new User(username,password);
        users.add(utente);

        System.out.printf("Ho registrato l'utente %s", username);

        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        //Serializzo la struttura dati degli utenti;
        try {
            mapper.writeValue(new File(filename),users);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void registerCallBack(String user,NotifyInterface ClientInterface) throws RemoteException{
        //Registrazione di un client per la CallBack;
        if(!clientList.containsKey(user)){
            clientList.put(user,ClientInterface);
            System.out.println("User "+user+" registered for Callback!");
        }
    }

    public synchronized void unregisterCallBack(String user) throws RemoteException{
        //Deregistrazione di un client per la CallBack;
        if(clientList.containsKey(user)){
            clientList.remove(user);
            System.out.println("User "+user+" unregistered for Callback!");
        }
    }

    public synchronized void doCallBack(String currUser,String user, String docName) throws RemoteException {
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        NotifyInterface clientStub = clientList.get(user);

        //Invio la notifica "live" al client se l'utente è Online;
        try {
            clientStub.notifyEvent(currUser, docName);
        }catch(NullPointerException e){

            //Nel caso in cui l'utente sia Offline aggiungo la notifica alla sua MessageBox e serializzo la Struttura dati degli utenti;
            String msg = "L'utente "+currUser+" ti ha invitato alla modifica del documento "+docName;
            users.get(indexOf(user)).addMsg(msg);
            try {
                mapper.writeValue(new File(filename),users);
            } catch (IOException e1) {
                System.out.println("Errore nella serializzazione!");
            }
            System.out.println("Messaggio aggiunto alla MessageBox di "+user);
        }
    }

    public int indexOf(String username){

        for(int i = 0; i < users.size();i++){
            if(users.get(i).getUsername().equals(username)) {
                return i;
            }
        }

        return -1;
    }
}
