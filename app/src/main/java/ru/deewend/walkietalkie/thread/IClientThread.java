package ru.deewend.walkietalkie.thread;

import java.io.DataOutputStream;

public interface IClientThread {
    /* public */ IServerThread getParent();
    /* public */ String getUsername();
    /* public */ DataOutputStream getOutputStream();
    /* public */ boolean isLoggedIn();
}
