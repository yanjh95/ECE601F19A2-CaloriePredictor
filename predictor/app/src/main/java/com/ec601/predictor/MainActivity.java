package com.ec601.predictor;

import android.Manifest;
        import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
        import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

        import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
        import androidx.appcompat.widget.Toolbar;
        import androidx.core.app.ActivityCompat;
        import androidx.core.content.ContextCompat;

import android.provider.MediaStore;
        import android.util.Log;
        import android.view.View;
        import android.view.Menu;
        import android.view.MenuItem;
        import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;


import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.custom.FirebaseCustomLocalModel;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelInterpreterOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import static com.google.firebase.database.Logger.Level.DEBUG;

public class MainActivity extends AppCompatActivity {

    private EditText calorie_label;
    private Button takePictureButton;
    private ImageView imageView;
    private Uri file;
    Uri fileUri;
    private Bitmap input_bitmap;
    private RadioGroup radioGroup;
    private String[] labels;
    private String classified_label;
    FirebaseUser user;
    String TAG = "Main Activity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        // ADD DEBUG LOGGING!!!
        database.setLogLevel(DEBUG);
        labels = new String[1000];
        try {
            getLabels();
        } catch(IOException e){
            e.printStackTrace();
        }
        classified_label = "";
        if(!checkCurrentUser()) {
            createSignInIntent();
        }
        Log.e("signed in already", "l");
        calorie_label = (EditText) findViewById(R.id.calorie_count);
        calorie_label.setFocusable(false);
        calorie_label.setClickable(false);

        takePictureButton = (Button) findViewById(R.id.button_image);
        imageView = (ImageView) findViewById(R.id.imageView);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            takePictureButton.setEnabled(false);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                takePictureButton.setEnabled(true);
            }
        }
    }
    public boolean checkCurrentUser() {
        // [START check_current_user]
        user = FirebaseAuth.getInstance().getCurrentUser();
//        writeToDatabase();

        if (user != null) {
            return true;
        } return false;
        // [END check_current_user]
    }

    private int RC_SIGN_IN = 1234;
    public void createSignInIntent() {
        Log.e("Clicking sign in" , "12345");
        // [START auth_fui_create_intent]
        // Choose authentication providers
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.EmailBuilder().build(),
                new AuthUI.IdpConfig.GoogleBuilder().build());

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .setIsSmartLockEnabled(false)
                        .build(),
                RC_SIGN_IN);
        // [END auth_fui_create_intent]
    }

    public boolean signOut(MenuItem view) {
        // [START auth_fui_signout]
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        createSignInIntent();
                    }
                });
        return true;
        // [END auth_fui_signout]
    }

    String mCurrentPhotoPath;
    int TAKE_PHOTO_REQUEST = 2222;
    public void takePicture(View view) {
        ContentValues values = new ContentValues(1);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpg");
        fileUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                            values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(intent.resolveActivity(getPackageManager()) != null) {
            mCurrentPhotoPath = fileUri.toString();
            Log.d(mCurrentPhotoPath, "path to file");
            intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, TAKE_PHOTO_REQUEST);
        }
    }

    int PICK_IMAGE = 3333;
    public void loadPicture(View view){
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
    }

    public void viewLog(View view){
        Intent intent = new Intent(this, CalorieLogActivity.class);
        startActivity(intent);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK
                && requestCode == TAKE_PHOTO_REQUEST) {
            InputStream inputStream = null;
            try {
                inputStream = getContentResolver().openInputStream(fileUri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            Bitmap bmp = BitmapFactory.decodeStream(inputStream);
            input_bitmap = bmp;
            if( inputStream != null ) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bmp != null) {
                imageView.setImageBitmap(bmp);
            }
            processCapturedPhoto();

        }
        else if (resultCode == Activity.RESULT_OK
                && requestCode == PICK_IMAGE) {
            try {
                InputStream inputStream = this.getContentResolver().openInputStream(data.getData());
                Bitmap bmp = BitmapFactory.decodeStream(inputStream);
                input_bitmap = bmp;
                if (bmp != null) {
                    imageView.setImageBitmap(bmp);
                }
                Log.d("Set Image View w/ Load", "Successful");
                loadMobileNetModel();
            } catch(FileNotFoundException e){
                e.printStackTrace();
            }
        }
        else if (resultCode == Activity.RESULT_OK
                && requestCode == RC_SIGN_IN) {
            user = FirebaseAuth.getInstance().getCurrentUser();
            Log.e(user.getDisplayName(), "123");
            checkUserExists(user); // Checks if user exists, if not adds the user.

        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }
    private void processCapturedPhoto() {
        loadMobileNetModel();
        if (!mCurrentPhotoPath.isEmpty() || mCurrentPhotoPath!=null) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            File f = new File(mCurrentPhotoPath);
            Uri contentUri = Uri.fromFile(f);
            //        imageView.setImageURI(contentUri);
            mediaScanIntent.setData(contentUri);
            this.sendBroadcast(mediaScanIntent);
        }
    }

    private void loadMobileNetModel(){
        Log.e(TAG, "Loading mobile net");
        classified_label = "";
        FirebaseCustomLocalModel localModel = new FirebaseCustomLocalModel.Builder()
                .setAssetFilePath("mobilenet_model.tflite")
                .build();

        FirebaseModelInterpreter firebaseInterpreter;
        try {
            FirebaseModelInterpreterOptions options =
                    new FirebaseModelInterpreterOptions.Builder(localModel).build();
            firebaseInterpreter = FirebaseModelInterpreter.getInstance(options);

            FirebaseModelInputOutputOptions inputOutputOptions;
            inputOutputOptions =
                    new FirebaseModelInputOutputOptions.Builder()
                            .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 224, 224, 3})
                            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 1000})
                            .build();

            float[][][][] input = convertBitmap(input_bitmap);
            FirebaseModelInputs inputs = null;
            inputs = new FirebaseModelInputs.Builder()
                    .add(input)  // add() as many input arrays as your model requires
                    .build();
            firebaseInterpreter.run(inputs, inputOutputOptions)
                    .addOnSuccessListener(
                            new OnSuccessListener<FirebaseModelOutputs>() {
                                @Override
                                public void onSuccess(FirebaseModelOutputs result) {
                                    // ...
                                    float[][] output = result.getOutput(0);
                                    float[] probabilities = output[0];
//                                    for (float p:probabilities) {
//                                        Log.e("Probs ", String.valueOf(p));
//                                    }
                                    int max_probility_loc = argmax(probabilities);
                                    String label = labels[max_probility_loc];
                                    classified_label = label;
                                    Log.e("CLASSIFICATION", label);
                                    if (classified_label.equals("cheeseburger") || classified_label.equals("hotdog") || classified_label.equals("bagel")) {
                                        Log.e(TAG, "Running actual predictor.");
                                        loadModel();
                                    } else{
                                        Toast.makeText(MainActivity.this, "Our models cannot predict this type of food.", Toast.LENGTH_LONG).show();
                                    }
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    // Task failed with an exception
                                    // ...
                                    classified_label = "";
                                    e.printStackTrace();
                                    Log.e("ERROR WRITING RESULT", "E");
                                }
                            });

        } catch (FirebaseMLException e){
            e.printStackTrace();
        }
    }
    private int argmax(float[] probabilities){
        float largest = probabilities[0];
        int index = 0;

        for (int i = 1; i < probabilities.length; i++) {
            if ( probabilities[i] >= largest ) {
                largest = probabilities[i];
                index = i;
            }
        }
        return index;
    }
    private void getLabels() throws IOException {
        Log.i(TAG, "Loading labels from assets/labels.txt");
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open("labels.txt")));
        for (int i = 0; i < 1000; i++) {
            String label = reader.readLine();
            labels[i] = label;
        }
        Log.i(TAG, "Labels loaded successfully");
    }
    private void loadModel(){
        FirebaseCustomLocalModel localModel = new FirebaseCustomLocalModel.Builder()
                .setAssetFilePath(getModelPath())
                .build();

        FirebaseModelInterpreter firebaseInterpreter;
        try {
            FirebaseModelInterpreterOptions options =
                    new FirebaseModelInterpreterOptions.Builder(localModel).build();
            firebaseInterpreter = FirebaseModelInterpreter.getInstance(options);

            FirebaseModelInputOutputOptions inputOutputOptions;
            inputOutputOptions =
                    new FirebaseModelInputOutputOptions.Builder()
                            .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 224, 224, 3})
                            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 1})
                            .build();

            float[][][][] input = convertBitmap(input_bitmap);
            FirebaseModelInputs inputs = null;
            inputs = new FirebaseModelInputs.Builder()
                    .add(input)  // add() as many input arrays as your model requires
                    .build();
            firebaseInterpreter.run(inputs, inputOutputOptions)
                    .addOnSuccessListener(
                            new OnSuccessListener<FirebaseModelOutputs>() {
                                @Override
                                public void onSuccess(FirebaseModelOutputs result) {
                                    // ...
                                    float[][] output = result.getOutput(0);
                                    float calorie_count = output[0][0];
                                    Log.e("RESULT ", String.valueOf(calorie_count));
                                    calorie_label.setText(String.valueOf(calorie_count));
                                    addCalorie(calorie_count);
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    // Task failed with an exception
                                    // ...
                                    e.printStackTrace();
                                    Log.e("ERROR WRITING RESULT", "E");
                                }
                            });

        } catch (FirebaseMLException e){
            e.printStackTrace();
        }
    }
    private float[][][][] convertBitmap(Bitmap bitmap){
        bitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
        Log.d("Bitmap", bitmap.toString());
        int batchNum = 0;
        float[][][][] input = new float[1][224][224][3];
        for (int x = 0; x < 224; x++) {
            for (int y = 0; y < 224; y++) {
                int pixel = bitmap.getPixel(x, y);
                // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                // model. For example, some models might require values to be normalized
                // to the range [0.0, 1.0] instead.
                input[batchNum][x][y][0] = (Color.red(pixel) - 127) / 128.0f;
                input[batchNum][x][y][1] = (Color.green(pixel) - 127) / 128.0f;
                input[batchNum][x][y][2] = (Color.blue(pixel) - 127) / 128.0f;
            }
        }
        return input;
    }
    private String getModelPath(){
        if (classified_label.equals("cheeseburger") || classified_label.equals("hotdog") || classified_label.equals("bagel")) {
            Log.e(TAG, classified_label + " was identified.");
            return "model-3.tflite";
        } else{
            Log.e(TAG, "Pizza selected");
            return "model-2.tflite";
        }


    }

    private void checkUserExists(FirebaseUser u){
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference userNameRef = rootRef.child("users").child(u.getUid());
        ValueEventListener eventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(!dataSnapshot.exists()) {
                    //create new user
                    addUser();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, databaseError.getMessage()); //Don't ignore errors!
            }
        };
        userNameRef.addListenerForSingleValueEvent(eventListener);
    }

    private void addUser(){
        Log.e("Writing to DB", "W");
        // Write a message to the database
        if (user == null){
            Log.e(TAG, "No Firebase User Logged in");
        }
        String uid = user.getUid();
        String uname = user.getDisplayName();
        String email = user.getEmail();
        User u = new User(uname, email);
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference();
        myRef.child("users").child(uid).setValue(u);


        // [START read_message]
        // Read from the database
        // Read from the database
        ValueEventListener postListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get Post object and use the values to update the UI
                User post = dataSnapshot.getValue(User.class);
                Log.e(TAG, "Posted");
                // ...
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Getting Post failed, log a message
                Log.w(TAG, "loadPost:onCancelled", databaseError.toException());
                // ...
            }
        };
        // [END read_message]
    }
    private void addCalorie(float calorie){
        String uid = user.getUid();
        String foodtype = classified_label.substring(0,1).toUpperCase() + classified_label.substring(1).toLowerCase();
        Calorie c = new Calorie(calorie, foodtype);
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference();
        myRef.child("users").child(uid).child("meals").push().setValue(c);


        // [START read_message]
        // Read from the database
        // Read from the database
        ValueEventListener postListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get Post object and use the values to update the UI
                User post = dataSnapshot.getValue(User.class);
                Log.e(TAG, "Posted");
                // ...
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Getting Post failed, log a message
                Log.w(TAG, "loadPost:onCancelled", databaseError.toException());
                // ...
            }
        };
    }

}
