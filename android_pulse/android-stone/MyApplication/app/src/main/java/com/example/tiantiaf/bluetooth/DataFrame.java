package com.example.tiantiaf.bluetooth;

/**
 * Created by tiantiaf on 2017/5/9.
 */

public class DataFrame {
    private int pulse;

    public DataFrame(int pulse){
        this.pulse = pulse;
    }

    public void setPulse(int pressure){
        this.pulse = pulse;
    }

    public long getPulse() {
        return pulse;
    }
}
