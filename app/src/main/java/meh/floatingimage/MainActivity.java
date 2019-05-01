package meh.floatingimage;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {
    private static final int CODE_DRAW_OVER_OTHER_APP_PERMISSION = 1111;
    private static final int CODE_EXTERNAL_STORAGE_PERMISSION = 2222;
    private static final int CODE_GET_IMAGE = 3333;

    private boolean drawOverPermissionGranted = false;
    private boolean externalStoragePermissionGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        drawOverPermissionGranted = requestDrawOverPermission();
        externalStoragePermissionGranted = requestExternalStoragePermission();


        Intent intent = getIntent();
        String cmd = intent.getStringExtra("Command");

        if (cmd != null && cmd.equals("pick_image")){
            startPickImage();
        }


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (externalStoragePermissionGranted && drawOverPermissionGranted){
            initializeView();
        }
    }

    /**
     * Set and initialize the view elements.
     */
    private void initializeView() {
        findViewById(R.id.start_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent serviceIntent = new Intent(MainActivity.this, FloatingImageService.class);
                startService(serviceIntent);
            }
        });

        findViewById(R.id.pick_image_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPickImage();
            }
        });

        findViewById(R.id.unlock_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unlockImage();
            }
        });
    }


    private boolean requestExternalStoragePermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                 Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    CODE_EXTERNAL_STORAGE_PERMISSION);
            return false;
        } else {
            return true;
        }
    }

    private boolean requestDrawOverPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, CODE_DRAW_OVER_OTHER_APP_PERMISSION);
            return false;
        } else {
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CODE_DRAW_OVER_OTHER_APP_PERMISSION) {
            //Check if the permission is granted or not.
            if (resultCode == RESULT_OK) {
                drawOverPermissionGranted = true;
            } else { //Permission is not available
                Toast.makeText(this,
                        "Draw over other app permission not available. Closing the application",
                        Toast.LENGTH_SHORT).show();

                drawOverPermissionGranted = false;
                //finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

        if (requestCode == CODE_GET_IMAGE) {
            super.onActivityResult(requestCode, resultCode, data);

            if (resultCode == RESULT_OK) {
                Uri imageUri = data.getData();
                String file_path = getRealPathFromURI(imageUri);

                Intent serviceIntent = new Intent(MainActivity.this, FloatingImageService.class);
                serviceIntent.putExtra("ImagePath", file_path);
                startService(serviceIntent);
                finish();
            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CODE_EXTERNAL_STORAGE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    externalStoragePermissionGranted = true;
                } else {
                   externalStoragePermissionGranted = false;
                }
                break;
            }
        }
    }

    private void startPickImage(){
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Pick Image"),CODE_GET_IMAGE);
    }

    private void unlockImage(){
        Intent serviceIntent = new Intent(MainActivity.this, FloatingImageService.class);
        serviceIntent.putExtra("Command", "unlock_image");
        startService(serviceIntent);
    }

    private String getRealPathFromURI(Uri contentURI) {
        String filePath = "";
        String wholeID = DocumentsContract.getDocumentId(contentURI);

        // Split at colon, use second item in the array
        String id = wholeID.split(":")[1];

        String[] column = {MediaStore.Images.Media.DATA};

        // where id is equal to
        String sel = MediaStore.Images.Media._ID + "=?";

        Cursor cursor = this.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                column, sel, new String[]{id}, null);

        int columnIndex = cursor.getColumnIndex(column[0]);

        if (cursor.moveToFirst()) {
            filePath = cursor.getString(columnIndex);
        }
        cursor.close();
        return filePath;
    }
}
