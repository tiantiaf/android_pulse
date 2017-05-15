package com.example.tiantiaf.pulse;

import android.util.Log;

/**
 * Created by tiantiaf on 2017/5/14.
 */

public class HRV_Processing {
    public long currentTime;
    public long lastBeatTime;
    private int signal;
    private int threshold = 1024;
    private int RR_Interval;
    private int[] rate_Array = new int[10];

    private boolean firstBeat = false, secondBeat = false;

    private int localT, localP;
    private boolean isPulseFind = false;
    private int N = 0;
    private int offset;

    private int[] processDataBuf = new int[5];
    private long[] processTimeBuf = new long[5];
    private long[] rrPeakBuf = new long[2];

    public void setSignal(int signal, long currentTime) {
        int i = 0;

        this.signal = signal;
        this.currentTime = currentTime;
        for (i = 0; i < 4; i++)
        {
            processDataBuf[i] = processDataBuf[i + 1];
            processTimeBuf[i] = processTimeBuf[i + 1];
        }
        processDataBuf[4] = signal;
        processTimeBuf[4] = currentTime;
    }

    public void setPulseFind(boolean isPulseFind) {
        this.isPulseFind = isPulseFind;
    }

    private void trackLow() {
        if(signal < threshold && (N > (RR_Interval/5) * 3 ) ) {
            if (signal < localT && signal > 900) {
                localT = signal;
            }
        }
    }

    private void trackHigh() {
        if(signal > threshold && signal > localP ) {
            if(signal < 1150) {
                localP = signal;
            }
        }
    }
    private void isFirstAndSecondBeat() {
        if(secondBeat){
            secondBeat = false;
            for(int i = 0; i <= 9; i++){
                rate_Array[i] = RR_Interval;
            }
        }
        if(firstBeat){
            firstBeat = false;
            secondBeat = true;
            return;
        }
    }

    private void updateThreshold() {

        if (signal < threshold && isPulseFind == true){
            isPulseFind = false;
            offset = localP - localT;
            threshold = (offset * 2) / 3 + localT;
            localP = threshold;
            localT = threshold;
        }

        if (N > 2500){
            threshold = 1048;
            localP = 1024;
            localT = 1024;
            firstBeat = true;
            secondBeat = false;
            lastBeatTime = currentTime;
        }
    }

    public boolean findPulse() {
        N = (int) (currentTime - lastBeatTime);
        trackLow();
        trackHigh();

        if(N > 250) {
            if(signal > threshold && isPulseFind == false && (N > (RR_Interval / 5) * 3 ) ) {
                isPulseFind = true;
                RR_Interval = (int) (currentTime - lastBeatTime);
                lastBeatTime = currentTime;
            }
            Log.d("HRV", "Pulse Find: " + RR_Interval);
        }
        updateThreshold();

        return isPulseFind;
    }

    public void HRV_Processing(){
        initParameters();
    }

    public void initParameters() {
        RR_Interval = 600;                  // 600ms per beat = 100 Beats Per Minute (BPM)
        isPulseFind = false;
        currentTime = 0;
        lastBeatTime = 0;
        localP = 1024;                    // peak at 1/2 the input range of 0..1023
        localT = 1024;                    // trough at 1/2 the input range.
        threshold = 1048;                // threshold a little above the trough
        offset = 200;                           // beat amplitude 1/10 of input range.
        firstBeat = true;               // looking for the first beat
        secondBeat = false;             // not yet looking for the second beat in a row

        int i = 0;
        for ( i = 0; i < 3; i++) {
            processDataBuf[i] = 0;
            processTimeBuf[i] = 0;
        }

        rrPeakBuf[0] = 0;
        rrPeakBuf[1] = 0;

        Log.d("HRV Initialize", "HRV Initialize");
    }

    public int getIBI() {
        return RR_Interval;
    }

    public boolean calcRRInterval() {
        if (processDataBuf[1] <= processDataBuf[2] && processDataBuf[2] >= processDataBuf[3]
                && rrPeakBuf[1] + 500 < processTimeBuf[2]) {
            int diff = (processDataBuf[2] - processDataBuf[0]) +
                    (processDataBuf[2] - processDataBuf[4]);
            Log.d("HRV", "Pulse Diff " + diff);
            if (diff >= 6) {
                rrPeakBuf[0] = rrPeakBuf[1];
                rrPeakBuf[1] = processTimeBuf[2];
                RR_Interval = (int) (rrPeakBuf[1] - rrPeakBuf[0]);
                Log.d("HRV", "RR interval " + RR_Interval);
                return true;
            }
        }
        return false;
    }
}
