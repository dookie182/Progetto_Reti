package Exceptions;

public class NotAllowedSharingException extends Exception {

    public NotAllowedSharingException(){
        super("Non sei il creatore del documento: non sei autorizzato a condividerlo!");
    }
}
