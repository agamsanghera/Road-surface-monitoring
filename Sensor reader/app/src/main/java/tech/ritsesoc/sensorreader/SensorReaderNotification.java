package tech.ritsesoc.sensorreader;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class SensorReaderNotification extends Application {
    public static final String NOTIF_ID = "SensorReader";

    @Override
    public void onCreate() {
        super.onCreate();

        createNotoficationChannel();
    }

    public void createNotoficationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel notificationChannel = new NotificationChannel(
                    NOTIF_ID,
                    "Sensor Reader",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(notificationChannel);

        }
    }
}