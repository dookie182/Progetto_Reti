package Server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotifyInterface extends Remote {

    void notifyEvent(String user,String docName) throws RemoteException;

}
