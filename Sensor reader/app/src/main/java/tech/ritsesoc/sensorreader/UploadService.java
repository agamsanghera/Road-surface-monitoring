package tech.ritsesoc.sensorreader;

import static tech.ritsesoc.sensorreader.SensorReaderNotification.NOTIF_ID;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.appwrite.Client;
import io.appwrite.ID;
import io.appwrite.models.InputFile;
import io.appwrite.services.Storage;
import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import tech.ritsesoc.sensorreader.UploadActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import io.appwrite.Client;
import io.appwrite.ID;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.InputFile;
import io.appwrite.services.Account;
import io.appwrite.services.Storage;
import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import tech.ritsesoc.sensorreader.UploadActivity;
public class UploadService extends Service {
    String csvpath1=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath()  + "/SensorReaderData/";
    String imagepath1=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/SensorReaderData/";
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private final UploadBinder binder = new UploadBinder();
    Context context;

    // for signaling UploadActivity that the uploading has finished. See onDestroy()
    public static MutableLiveData<Boolean> isUploadFinished = new MutableLiveData<>(false);

    public static MutableLiveData<Integer> uploadedFileCount = new MutableLiveData<>(0);
    public static MutableLiveData<Integer> uploadFailFileCount = new MutableLiveData<>(0);


    @Override
    public void onCreate() {
        super.onCreate();
        context=getApplicationContext();

        powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sensorreader:wakeLockFragment3");
    }
    class UploadBinder extends Binder {
        public UploadService getService() {
            return UploadService.this;
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void upload() {
        Log.i("Client", UploadActivity.client.getEndPoint());
        Storage storage = new Storage(UploadActivity.client);

        ArrayList<File> upload_list = new ArrayList<>();
        AtomicInteger file_uploaded_count = new AtomicInteger(0); // used to keep track how many files have been successfully uploaded
        AtomicInteger file_upload_fail_count = new AtomicInteger(0); // count for failed-to-upload files

        // Handler is used to update elements in UI (Main) Thread.
        Handler handler = new Handler(Looper.getMainLooper());

        File csv_directory = new File(csvpath1);
        File image_directory = new File(imagepath1);
        File[] csv_files = csv_directory.listFiles();
        File[] image_files = image_directory.listFiles();

        // list file of each folder and append it on the upload_list ArrayList.
        int imageNum = image_files.length;
        int csvNum = csv_files.length;
        if (image_files != null) {
            for (int i = 0; i < imageNum; i++) {
                upload_list.add(image_files[i]);
            }
        }
        if (csv_files != null) {
            for (int i = 0; i < csvNum; i++) {
                upload_list.add(csv_files[i]);
            }
        }

        context = getApplicationContext();
        Log.i("Context", context.toString());

        // wakeLock is used to keep the CPU active while uploading
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sensorreader:wakeLockFragment3");

        // setReferenceCounted is set to true since the uploading process takes place on different threads on the SDK
        // as such to properly turn off wakeLock, release() needs to be called with the same number of times aas acquire()
        wakeLock.setReferenceCounted(true);

        // Upload every file
        for (final File curr_file : upload_list) {
            // final String fileID = subString(curr_file.getName());

            Log.d("File Upload", "Uploading File: " + curr_file.toString());
            wakeLock.acquire();

            storage.createFile(
                    "649e48d74d12f8f69835",
                    ID.Companion.unique(), // ID.Companion.custom(subString(name)), // this causes large files cannot be send to the server, changing it to unique()
                    InputFile.Companion.fromFile(curr_file),
                    new Continuation<Object>() {
                        @NotNull
                        @Override
                        public CoroutineContext getContext() {
                            return EmptyCoroutineContext.INSTANCE;
                        }

                        @Override
                        public void resumeWith(@NotNull Object o) {
                            try {
                                if (o instanceof Result.Failure) {
                                    Result.Failure failure = (Result.Failure) o;
                                    if (wakeLock.isHeld()) {
                                        wakeLock.release();
                                    }
                                    throw failure.exception;
                                } else if (o instanceof io.appwrite.models.File){
                                    io.appwrite.models.File response = (io.appwrite.models.File) o;
                                    if (response.getChunksUploaded() == response.getChunksTotal()) {
                                        Log.d("File Upload", "Finished Uploading " + response.getName() + " (" + response.getId() + ") ");

                                        File delete_file = curr_file;
                                        if (delete_file.exists()){
                                            delete_file.delete();
                                        }

                                        if (wakeLock.isHeld()) {
                                            wakeLock.release();
                                        }

                                        uploadedFileCount.postValue(file_uploaded_count.incrementAndGet());
                                    }
                                } else {
                                    Log.d("File Upload", "Something went wrong.");
                                }
                            } catch (Throwable th) {
                                uploadedFileCount.postValue(file_uploaded_count.incrementAndGet());
                                uploadFailFileCount.postValue(file_upload_fail_count.incrementAndGet());
                                Log.e("ERROR", th.toString());
                                th.printStackTrace();
                            }
                        }
                    }
            );
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        wakeLock.acquire();

        Intent notificationIntent = new Intent(this, UploadActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0,notificationIntent,PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this,NOTIF_ID)
                .setContentTitle("Road Monitoring")
                .setContentText("Uploading Files..")
                .setSmallIcon(R.drawable.ic_golf_course_black_24dp)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);

        upload();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isUploadFinished.postValue(true); // to flag to upload activity that the uploading is done
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
