package com.liyangbin.cartrofit;

public class CartrofitGrammarException extends RuntimeException {
    public CartrofitGrammarException(String msg) {
        super(msg);
    }

    public CartrofitGrammarException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public CartrofitGrammarException(Throwable cause) {
        super(cause);
    }
}
