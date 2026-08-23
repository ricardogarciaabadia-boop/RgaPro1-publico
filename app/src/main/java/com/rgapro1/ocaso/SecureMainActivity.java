package com.rgapro1.ocaso;

import android.content.SharedPreferences;
import android.os.Bundle;
import com.rgapro1.ocaso.data.local.LegacyDataMigrator;

/** Secure launcher for the focused RgaPro UI. */
public class SecureMainActivity extends MainActivityCore {
    private SharedPreferences secureLocalPreferences;
    @Override public SharedPreferences getSharedPreferences(String name,int mode){
        if(!"rgapro_local".equals(name)) return super.getSharedPreferences(name,mode);
        if(secureLocalPreferences==null){SharedPreferences delegate=super.getSharedPreferences(name,mode);secureLocalPreferences=new SecurePinPreferences(delegate,new SecurePinStore(getApplicationContext()));}
        return secureLocalPreferences;
    }
    @Override public void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);new Thread(()->LegacyDataMigrator.migrate(getApplicationContext()),"rgapro-room-migration").start();ClientAutoLinker.start(this);}
}
