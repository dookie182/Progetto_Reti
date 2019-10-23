package Exceptions;

public class NotAuthorizedUserException extends Exception {

    public NotAuthorizedUserException(){

        super("-1Non sei autorizzato a visualizzare o modificare il documento!");
    }
}
