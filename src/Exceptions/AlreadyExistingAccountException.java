package Exceptions;

public class AlreadyExistingAccountException extends RuntimeException{

    public AlreadyExistingAccountException(){
        super("Account già esistente");
    }

}
