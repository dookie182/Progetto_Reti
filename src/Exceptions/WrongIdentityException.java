package Exceptions;

public class WrongIdentityException extends Exception {

    public WrongIdentityException(){
        super("Credenziali Errate!");
    }
}
