package Exceptions;

public class MissingAccountException extends Exception {

    public MissingAccountException(){
        super("Account non esistente: effettuare la registrazione!");
    }
}
