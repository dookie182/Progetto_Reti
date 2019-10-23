package Client;

import Server.NotifyInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

public class MyNotify extends RemoteObject implements NotifyInterface {

    public MyNotify(){
        super();
    }

    public void notifyEvent(String user,String docName) throws RemoteException {
        System.out.println("L'utente "+user+" ti ha invitato alla modifica del documento "+docName);
    }
}
