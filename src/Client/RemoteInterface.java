package Client;

import Exceptions.AlreadyExistingAccountException;
import Server.NotifyInterface;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteInterface extends Remote {

     void register(String username,String passw) throws RemoteException, AlreadyExistingAccountException;

     void registerCallBack(String user, NotifyInterface ClientInterface) throws RemoteException;

     void unregisterCallBack(String user) throws RemoteException;

     void doCallBack(String currUser,String user,String docName) throws RemoteException;


}
