/*
 * FragmentPage3: Upload files
 * Handles connecting and uploading to the backend server.
 */

package tech.ritsesoc.sensorreader;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import tech.ritsesoc.sensorreader.UploadService;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

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

public class UploadActivity extends AppCompatActivity {
    public static String csvpath;
    public static String imagepath;
    UploadService service;
    // Interface
    TextView textViewUploadedFile;
    Button buttonUpload;
    Button buttonDisplay;
    Button buttonRefresh;
    Button buttonReduce;
    Switch switchIsConnectedWifi;
    ListView listview;

    // TODO: insert appwrite stuff here
    // Appwrite backend
    static Client client;
    Account account;

    // File management
    private File newFile;
    private BufferedWriter bw;
    private FileWriter fw;

    // Upload Service: LiveData for observing upload progress or finish state
    public LiveData<Boolean> uploadStatus;
    public LiveData<Integer> uploadedFileCount;
    public LiveData<Integer> uploadFailFileCount;

    private AlertDialog.Builder adUpload;
    private AlertDialog adUploadDialog;
    private AlertDialog.Builder adFail;
    private AlertDialog adFailDialog;

    // Executed when the UI is viewed/opened, for initializing stuff.
    @Nullable
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        service= new UploadService();
        setContentView(R.layout.upload_activity);

        buttonUpload = (Button) findViewById(R.id.buttonUpload);
        buttonReduce = (Button) findViewById(R.id.buttonReduceSize);
        textViewUploadedFile = (TextView) findViewById(R.id.textViewUploadedFile);

        csvpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath()  + "/SensorReaderData/";
        imagepath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/SensorReaderData/";
        switchIsConnectedWifi = (Switch) findViewById(R.id.switchIsConnectedWifi);
        listview = (ListView) findViewById(R.id.listView);
        buttonDisplay = (Button) findViewById(R.id.buttonDisplay);
        buttonRefresh = (Button) findViewById(R.id.buttonRefresh);



        // Initialize session with the appwrite server
        accountInit();

        // Set up how the upload button behaves when clicked.
        buttonUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ConnectivityManager connManager = (ConnectivityManager) UploadActivity.this.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = connManager.getActiveNetworkInfo();

                // If no internet connection, abort the function.
                if (activeNetwork == null) {
                    Toast.makeText(UploadActivity.this, R.string.toast_no_connection, Toast.LENGTH_SHORT).show();
                    return;
                }

                // If the "upload on wifi network" switch is true, then upload with wifi.
                // If false, then upload with mobile data.
                if (switchIsConnectedWifi.isChecked()) {
                    if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) { // Check wifi connection
                        upload();
                    } else {
                        Toast.makeText(UploadActivity.this, R.string.toast_no_wifi_connection, Toast.LENGTH_SHORT).show();
                    }
                } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) { // if mobile data
                    upload();
                } else {
                    Toast.makeText(UploadActivity.this, R.string.toast_no_connection, Toast.LENGTH_SHORT).show();
                }
            }
        });
        buttonDisplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder ad1 = new AlertDialog.Builder(UploadActivity.this);

                ad1.setIcon(android.R.drawable.ic_menu_search);
                ad1.setTitle(R.string.file_location);
                ad1.setMessage(getResources().getString(R.string.txt_file_path, csvpath, imagepath));
                ad1.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                AlertDialog ad = ad1.create();
                ad.show();
            }
        });

        buttonRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFile();
            }
        });


        // Set up how the "reduce file size" button behaves when clicked.
        // TODO: look into the battery optimizations on these commented parts in this function.
        buttonReduce.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View V) {
                File directory1 = new File(csvpath);
                File[] files1 = directory1.listFiles();
                int files1Length;

                // Sometimes the files1 array is null, so we need to check for it.
                if (files1 != null) {
                    files1Length = files1.length;
                } else {
                    files1Length = 0;
                }

                if (files1Length > 0) {
                    for (File file : files1) {
                        reduce_files(file.getAbsolutePath());
                    }
                    Toast.makeText(UploadActivity.this, "Reduce Data size finished.", Toast.LENGTH_SHORT).show();
                    showFile();
                } else {
                    Toast.makeText(UploadActivity.this, R.string.toast_no_file, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // onResume: the code below is executed just before the activity runs.
    @Override
    public void onResume() {
        super.onResume();
        boolean permission = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permission = (UploadActivity.this.checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        }

        Log.d("Fragment3", "is write permission?: " + String.valueOf(permission));
        if (!permission) {
            textViewUploadedFile.setText(R.string.warning_file_cant_read);
            buttonUpload.setEnabled(false);
        } else {
            showFile();
        }
    }

    public void accountInit() {
        client = new Client(UploadActivity.this)
                .setEndpoint("https://cloud.appwrite.io/v1")
                .setProject(String.valueOf(R.string.Appwrite_Token));

        account = new Account(client);

        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    Log.d("Appwrite", "Account get error, creating new anonymous session...");
                    account.createAnonymousSession(new CoroutineCallback<>((result_, error_) -> {
                        if (error_ != null) {
                            error_.printStackTrace();
                            Log.d("Fragment 3", "Cannot create anonymous session:" + error_.toString());
                            return;
                        }
                        Log.d("Fragment 3", result_.toString());
                    }));
                    return;
                }
                Log.d("Appwrite", result.toString());
            }));
        } catch (AppwriteException e) {
            Log.d("Appwrite", "Something went wrong.");
            throw new RuntimeException(e);
        }
    }

    // get magnitude for reduce file function
    public ArrayList<Float> get_magnitude(ArrayList<Float> accx, ArrayList<Float> accy, ArrayList<Float> accz, ArrayList<Float> gyrx, ArrayList<Float> gyry, ArrayList<Float> gyrz){
        ArrayList<Float> magnitude = new ArrayList<Float>();

        for (int i=0; i<accx.size(); i++){
            float mag = (float) Math.sqrt(accx.get(i)*accx.get(i)+accy.get(i)*accy.get(i)+accz.get(i)*accz.get(i)+gyrx.get(i)*gyrx.get(i)+gyry.get(i)*gyry.get(i)+gyrz.get(i)*gyrz.get(i));
            magnitude.add(mag);
        }

        return magnitude;
    }

    // max-min norm for reduce file function
    public ArrayList<Float> maxMinNorm(ArrayList<Float> val, float max, float min){
        ArrayList<Float> normVal = new ArrayList<>();
        for (int i=0; i<val.size(); i++){
            normVal.add((val.get(i)-min)/(max-min));
        }
        return normVal;
    }

    // calculate variance for reduce file function
    public ArrayList<Float> getVariances(ArrayList<Float> data, int varWindowLength){
        ArrayList<Float> variances = new ArrayList<>();
        for (int i=0; i<data.size()-varWindowLength; i++){
            float average = 0;
            for (int j=0; j <varWindowLength; j++){
                average += data.get(i+j);
            }
            average = average/varWindowLength;

            float var_temp = 0;
            for (int j=0; j <varWindowLength; j++){
                var_temp += ((data.get(i+j)-average)*(data.get(i+j)-average));
            }
            var_temp = var_temp/(varWindowLength-1);

            variances.add(var_temp);
        }
        return variances;
    }

    // for reduce file funcion
    public boolean is_flat(ArrayList<Float> subseriesVariant, float threshold){
        int total99 = 0;
        for (int i = 0; i < subseriesVariant.size(); i++){
            if (subseriesVariant.get(i) >= threshold) {
                total99 += 1;
            }
        }
        if (total99 >= 15) {
            return false;
        }
        else {
            return true;
        }
    }

    // gets called when the reduce file size button is pressed.
    public void reduce_files(String fpath){
        float threshold;
        ArrayList<Float> magnitude = new ArrayList<>();
        ArrayList<Float> variance = new ArrayList<>();
        ArrayList<Float> accX = new ArrayList<>();
        ArrayList<Float> accY = new ArrayList<>();
        ArrayList<Float> accZ = new ArrayList<>();

        ArrayList<Float> gyrX = new ArrayList<>();
        ArrayList<Float> gyrY = new ArrayList<>();
        ArrayList<Float> gyrZ = new ArrayList<>();

        ArrayList<Float> magX = new ArrayList<>();
        ArrayList<Float> magY = new ArrayList<>();
        ArrayList<Float> magZ = new ArrayList<>();

        ArrayList<Float> magA = new ArrayList<>();
        ArrayList<Float> magP = new ArrayList<>();
        ArrayList<Float> magR = new ArrayList<>();

        ArrayList<Float> graX = new ArrayList<>();
        ArrayList<Float> graY = new ArrayList<>();
        ArrayList<Float> graZ = new ArrayList<>();

        ArrayList<Float> lat = new ArrayList<>();
        ArrayList<Float> lon = new ArrayList<>();

        ArrayList<Float> speed = new ArrayList<>();
        ArrayList<Float> timestep = new ArrayList<>();

        ArrayList<String> time_stamp = new ArrayList<>();

        ArrayList<Integer> class1 = new ArrayList<>();
        ArrayList<Integer> class2 = new ArrayList<>();

        ArrayList<Float> accX_norm = null;
        ArrayList<Float> accY_norm = null;
        ArrayList<Float> accZ_norm = null;
        ArrayList<Float> gyrX_norm = null;
        ArrayList<Float> gyrY_norm = null;
        ArrayList<Float> gyrZ_norm = null;

        String rawLines = "";
        try{
            BufferedReader br = new BufferedReader(new FileReader(fpath));
            while ((rawLines = br.readLine()) != null)  //returns a boolean value
            {
                String[] line = rawLines.split(",");

                // depending on when the user stops recording, the line may not be fully completed.
                // this skips the incomplete line.
                if(line.length < 11) {
                    continue;
                }

                accX.add(Float.parseFloat(line[0]));
                accY.add(Float.parseFloat(line[1]));
                accZ.add(Float.parseFloat(line[2]));

                gyrX.add(Float.parseFloat(line[3]));
                gyrY.add(Float.parseFloat(line[4]));
                gyrZ.add(Float.parseFloat(line[5]));

//                magX.add(Float.parseFloat(line[6]));
//                magY.add(Float.parseFloat(line[7]));
//                magZ.add(Float.parseFloat(line[8]));
//
//                magA.add(Float.parseFloat(line[9]));
//                magP.add(Float.parseFloat(line[10]));
//                magR.add(Float.parseFloat(line[11]));
//
//                graX.add(Float.parseFloat(line[12]));
//                graY.add(Float.parseFloat(line[13]));
//                graZ.add(Float.parseFloat(line[14]));

                lat.add(Float.parseFloat(line[6]));
                lon.add(Float.parseFloat(line[7]));

                speed.add(Float.parseFloat(line[8]));
                timestep.add(Float.parseFloat(line[9]));
                time_stamp.add(line[10]);

                //class1.add(Integer.parseInt(line[20]));
                //class2.add(Integer.parseInt(line[21]));
            }
            br.close();  //closes the scanner

            accX_norm = maxMinNorm(accX, 45, -45);
            accY_norm = maxMinNorm(accY, 45, -45);
            accZ_norm = maxMinNorm(accZ, 45, -45);
            gyrX_norm = maxMinNorm(gyrX, 11, -11);
            gyrY_norm = maxMinNorm(gyrY, 11, -11);
            gyrZ_norm = maxMinNorm(gyrZ, 11, -11);

        } catch(Exception e){
            e.printStackTrace();
            Toast.makeText(UploadActivity.this, "File hilang!", Toast.LENGTH_SHORT).show();
        }

        magnitude = get_magnitude(accX_norm, accY_norm, accZ_norm, gyrX_norm, gyrY_norm, gyrZ_norm);
        variance = getVariances(magnitude, 15);
        ArrayList<Float> sortedVariance = new ArrayList<>();
        sortedVariance = (ArrayList<Float>)variance.clone();

        Collections.sort(sortedVariance);
        threshold = sortedVariance.get((int)(sortedVariance.size()*0.75));

        ArrayList<Integer> start = new ArrayList<>();
        ArrayList<Integer> end = new ArrayList<>();
        int start_ = -1;
        int end_ = -1;

        for (int i=0; i< variance.size(); i+=64){//stride=win_length/2
            ArrayList<Float> sublist = new ArrayList<>();
            if (i+128 >= variance.size()){
                sublist = new ArrayList<Float>(variance.subList(i,variance.size()));
            }
            else {
                sublist = new ArrayList<Float>(variance.subList(i,i+128));
            }
            if (!is_flat(sublist, threshold) && start_ == -1) {
                start_ = i;
                start.add(start_);
            }
            else if (is_flat(sublist, threshold) && start_ != -1){
                end_ = i+(64);
                if (end_ > magnitude.size()) {
                    end_ = magnitude.size() - 1;
                }
                end.add(end_);
                start_ = -1;
            }
        }
        if (start_ != -1) {
            end.add(magnitude.size() - 1);
        }

        //write a new file based on start and end list (unflattened variance)
        for (int i=0; i< start.size(); i++) {
            try {
                newFile = new File(fpath + Integer.toString(i+1) + ".csv");
                if (!newFile.exists()) {
                    //newFile.mkdir();
                    newFile.createNewFile();
                }

                fw = new FileWriter(newFile);
                bw = new BufferedWriter(fw);
                for (int j=start.get(i); j<end.get(i); j++){
                    String record = accX.get(j) + "," + accY.get(j) + "," + accZ.get(j) + "," + gyrX.get(j) + "," + gyrY.get(j) + "," + gyrZ.get(j)+ "," + lat.get(j) + "," + lon.get(j) + "," + speed.get(j) + "," + timestep.get(j) + "," + time_stamp.get(j) +"\n";
                    bw.write(record);
                }
                bw.close();
                fw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String subString(String str) {
        int fileType = str.lastIndexOf(".");
        int sepPos = str.lastIndexOf("_");
        int len = str.length();
        return(str.substring(fileType+1, len) + str.substring(sepPos+1, len-4));
    }

//    public boolean isUploadFinished(MutableLiveData<Boolean> uploadStatus) {
//        uploadStatus.observe(this, new Observer<Boolean>() {
//            @Override
//            public void onChanged(Boolean aBoolean) {
//                return aBoolean;
//            }
//        });
//        return uploadStatus.getValue();
//    }

    public void upload() {
        // Check if there is no file.
        int csvCount = new File(csvpath).listFiles().length;
        int imageCount = new File(imagepath).listFiles().length;
        if (csvCount <= 0 && imageCount <= 0) {
            Toast.makeText(this, R.string.toast_no_file, Toast.LENGTH_LONG).show();
            return;
        }

        // Appwrite Initialization, just in case.
        accountInit();

        // Start the upload service
        Intent intent = new Intent(UploadActivity.this, UploadService.class);
        intent.putExtra("image_folder_path", imagepath);
        intent.putExtra("csv_folder_path",csvpath);
        Log.i("csv", csvpath.toString());
        Log.i("csv", imagepath.toString());
        startService(intent);

        // LiveData for observing
        uploadStatus = UploadService.isUploadFinished;
        uploadedFileCount = UploadService.uploadedFileCount;
        uploadFailFileCount = UploadService.uploadFailFileCount;

        // Display progress dialog
        Handler handler = new Handler(Looper.getMainLooper());
        adUpload = new AlertDialog.Builder(this);
        adUpload.setCancelable(false);
        adUpload.setIcon(android.R.drawable.ic_menu_upload);
        adUpload.setTitle(R.string.upload);
        adUpload.setMessage(getResources().getString(
                R.string.toast_uploading_file, 0, imageCount + csvCount));
        adUploadDialog = adUpload.create();
        adUploadDialog.show();

        // observe the number of uploaded files in UploadService and update the UI.
        uploadedFileCount.observe(UploadActivity.this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer file_uploaded_count) {
                adUploadDialog.setMessage(getResources().getString(
                        R.string.toast_uploading_file, file_uploaded_count, imageCount + csvCount));

                if(file_uploaded_count == (imageCount + csvCount)) {
                    if (uploadFailFileCount.getValue() > 0){
                        adFail = new AlertDialog.Builder(UploadActivity.this);
                        adFail.setCancelable(false);
                        adFail.setIcon(android.R.drawable.stat_sys_warning);
                        adFail.setTitle(R.string.warning_title);
                        adFail.setMessage(getResources().getString(R.string.toast_upload_fail, uploadFailFileCount.getValue()));
                        adFail.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        adFailDialog = adFail.create();
                        adFailDialog.show();
                    } else {
                        Toast.makeText(UploadActivity.this, R.string.txt_upload_finished, Toast.LENGTH_LONG).show();
                        adUploadDialog.dismiss();
                        showFile();
                    }
                }
            }
        });

//        // Create new thread to wait for the uploading process and keep track of the progress.
//        Runnable r = new Runnable() {
//            @Override
//            public void run() {
//                // for comparison if file_uploaded_count increased or not
//                int prev_count = 0;
//                // After finish uploading, run below.
//                handler.post(new Runnable() {
//                    @Override
//                    public void run() {
//
//
//
//                    }
//                });
//
//            }
//        };
//        new Thread(r).start();
//        adUploadDialog.show();
    }
    /*public void upload() {
        AlertDialog.Builder ad1 = new AlertDialog.Builder(UploadActivity.this);
        ad1.setCancelable(false);
        ad1.setIcon(android.R.drawable.ic_menu_upload);
        ad1.setTitle(R.string.upload);

        Storage storage = new Storage(client);

        File csv_directory = new File(csvpath);
        File image_directory = new File(imagepath);
        File[] csv_files = csv_directory.listFiles();
        File[] image_files = image_directory.listFiles();

        ArrayList<File> upload_list = new ArrayList<>();
        AtomicInteger file_uploaded_count = new AtomicInteger(0); // used to keep track how many files have been successfully uploaded
        AtomicInteger file_upload_fail_count = new AtomicInteger(0); // used to keep track how many files have been successfully uploaded

        // Handler is used to update elements in UI (Main) Thread.
        Handler handler = new Handler(Looper.getMainLooper());

        // list file of each folder and append it on the upload_list ArrayList.
        int csvNum;
        if (csv_files != null) {
            csvNum = csv_files.length;
            for (int i = 0; i < csvNum; i++) {
                upload_list.add(csv_files[i]);
            }
        } else {
            csvNum = 0;
        }

        int imageNum;
        if (image_files != null) {
            imageNum = image_files.length;
            for (int i = 0; i < imageNum; i++) {
                upload_list.add(image_files[i]);
            }
        } else {
            imageNum = 0;
        }

        // check if both folders does not have any file
        if (csvNum <= 0 && imageNum <= 0) {
            Toast.makeText(UploadActivity.this, R.string.toast_no_file, Toast.LENGTH_LONG).show();
            showFile();
            return;
        }

        // wakeLock is used to keep the CPU active while uploading
        PowerManager powerManager = (PowerManager) UploadActivity.this.getSystemService(UploadActivity.this.POWER_SERVICE);
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

                                        file_uploaded_count.incrementAndGet();
                                    }
                                } else {
                                    Log.d("File Upload", "Something went wrong.");
                                }
                            } catch (Throwable th) {
                                file_uploaded_count.incrementAndGet();
                                file_upload_fail_count.incrementAndGet();
                                Log.e("ERROR", th.toString());
                                th.printStackTrace();
                            }
                        }
                    }
            );
        }

        // Display progress dialog
        ad1.setMessage(getResources().getString(R.string.toast_uploading_file, file_uploaded_count.get(), upload_list.size()));
        AlertDialog ad;
        ad = ad1.create();

        // Create new thread to wait for the uploading process and keep track of the progress.
        Runnable r = new Runnable() {
            @Override
            public void run() {
                // for comparison if file_uploaded_count increased or not
                int prev_count = 0;

                while(file_uploaded_count.get() < upload_list.size()) {
                    // call post if only the number increases.
                    if (file_uploaded_count.get() != prev_count) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                ad.setMessage(getResources().getString(R.string.toast_uploading_file, file_uploaded_count.get(), upload_list.size()));
                            }
                        });
                        prev_count = file_uploaded_count.get();
                    }
                }

                // After finish uploading, run below.
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        ad.dismiss();

                        if (file_upload_fail_count.get() > 0){
                            AlertDialog.Builder adb2 = new AlertDialog.Builder(UploadActivity.this);
                            adb2.setCancelable(false);
                            adb2.setIcon(android.R.drawable.stat_sys_warning);
                            adb2.setTitle(R.string.warning_title);
                            adb2.setMessage(getResources().getString(R.string.toast_upload_fail, file_upload_fail_count.get()));
                            adb2.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            AlertDialog ad2 = adb2.create();
                            ad2.show();
                        } else {
                            Toast.makeText(UploadActivity.this, R.string.txt_upload_finished, Toast.LENGTH_LONG).show();
                        }

                        showFile();
                    }
                });

            }
        };
        new Thread(r).start();
        ad.show();
    }
*/
    public String calculateFileSize(File file) {
        float fileSize = (float) file.length();
        if (fileSize >= (1024 * 1024)) {
            return String.format("%.02f", fileSize / (1024 * 1024)) + " MB";
        } else {
            return String.format("%.02f", fileSize / 1024) + " KB";
        }
    }

    public void showFile(){
        // Checks the number of files that needs to be uploaded when the first tab is opened.
        File csv_directory = new File(csvpath);
        File image_directory = new File(imagepath);

        File[] csv_files = csv_directory.listFiles();
        File[] image_files = image_directory.listFiles();

        ArrayList<String> file_list = new ArrayList<>();
        String item_str;

        int csvNum;
        if (csv_files != null) {
            csvNum = csv_files.length;
            for (int i = 0; i < csvNum; i++) {
                item_str = "Report " + (i + 1) + " \t-\t " + calculateFileSize(csv_files[i]) + "\n"
                           + csv_files[i].getName();
                file_list.add(item_str);
            }
        } else {
            csvNum = 0;
        }

        int imageNum;
        if (image_files != null) {
            imageNum = image_files.length;
            for (int i = 0; i < imageNum; i++) {
                item_str = "Image " + (i + 1) + " \t-\t " + calculateFileSize(image_files[i]) + "\n"
                        + image_files[i].getName();
                file_list.add(item_str);
            }
        } else {
            imageNum = 0;
        }

        textViewUploadedFile.setText(getResources().getString(R.string.txt_record_count, csvNum, imageNum));

        buttonUpload.setEnabled(csvNum > 0 || imageNum > 0); // enable button only if there's anything in imagepath or csv path.
        buttonReduce.setEnabled(csvNum > 0 || imageNum > 0);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(UploadActivity.this, R.layout.my_custom_layout, file_list);
        listview.setAdapter(adapter);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if (adUploadDialog != null) {
            if (adUploadDialog.isShowing()) {
                adUploadDialog.dismiss();
            }
        }

        if (adFailDialog != null) {
            if (adFailDialog.isShowing()) {
                adFailDialog.dismiss();
            }
        }
    };
}