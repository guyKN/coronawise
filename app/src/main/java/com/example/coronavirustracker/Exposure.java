package com.example.coronavirustracker;

import java.sql.Timestamp;

public class Exposure {
    private Timestamp contactTime;
    private Timestamp verifiedExposureTime;
    public Exposure(Timestamp contactTime, Timestamp verifiedExposureTime){
        this.contactTime = contactTime;
        this.verifiedExposureTime = verifiedExposureTime;
    }

    public Timestamp getContactTime(){
        return this.contactTime;
    }
    public Timestamp getVerifiedExposureTime(){
        return this.verifiedExposureTime;
    }

    public String getMessage(){
            //return "At " + this.contactTime.toString() + "you were near someone who was verified to have the Coronavirus at " + this.verifiedExposureTime.toString();
        return "You are at risk of COVID-19!";
    }
}
