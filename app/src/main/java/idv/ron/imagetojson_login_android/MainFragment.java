package idv.ron.imagetojson_login_android;


import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileNotFoundException;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;

public class MainFragment extends Fragment {
    private static final String TAG = "MainActivity";
    private static final int REQ_TAKE_PICTURE = 0;
    private static final int REQ_CROP_PICTURE = 1;
    private static final int REQ_LOGIN = 2;
    private Activity activity;
    private Uri contentUri, croppedImageUri;
    private Bitmap picture;
    private MyTask dataUploadTask;
    private ImageView ivTakePicture;
    private Button btLogout;
    private TextView tvResultUser;
    private ImageView ivResultImage;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvResultUser = view.findViewById(R.id.tvResultUser);
        ivResultImage = view.findViewById(R.id.ivResponseImage);

        ivTakePicture = view.findViewById(R.id.ivTakePicture);
        ivTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File dir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                if (dir != null && !dir.exists()) {
                    if (!dir.mkdirs()) {
                        Log.e(TAG, getString(R.string.textDirNotCreated));
                        return;
                    }
                }
                File file = new File(dir, "picture.jpg");
                contentUri = FileProvider.getUriForFile(
                        activity, activity.getPackageName() + ".provider", file);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, contentUri);

                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                    startActivityForResult(intent, REQ_TAKE_PICTURE);
                } else {
                    showToast(activity, R.string.textNoCameraApp);
                }
            }
        });

        Button btUpload = view.findViewById(R.id.btUpload);
        btUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picture == null) {
                    showToast(activity, R.string.textNotUploadWithoutPicture);
                    return;
                }
                Intent loginIntent = new Intent(activity, LoginDialogActivity.class);//要去manifests設定 41行購物車
                startActivityForResult(loginIntent, REQ_LOGIN);//開啟都入畫面
            }
        });

        btLogout = view.findViewById(R.id.btLogout);
        btLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences pref = activity.getSharedPreferences(Common.PREF_FILE,
                        MODE_PRIVATE);
                pref.edit().putBoolean("login", false).apply();
                v.setVisibility(View.GONE);
            }
        });

    }


    @Override
    public void onResume() {
        super.onResume();
        // 從偏好設定檔中取得登入狀態來決定是否顯示「登出」
        SharedPreferences pref = activity.getSharedPreferences(Common.PREF_FILE,
                MODE_PRIVATE);
        boolean login = pref.getBoolean("login", false);
        if (login) {
            btLogout.setVisibility(View.VISIBLE);
        } else {
            btLogout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                // 手機拍照App拍照完成後可以取得照片圖檔
                case REQ_TAKE_PICTURE:
                    Log.d(TAG, "REQ_TAKE_PICTURE: " + contentUri.toString());
                    crop(contentUri);
                    break;

                case REQ_CROP_PICTURE:
                    Log.d(TAG, "REQ_CROP_PICTURE: " + croppedImageUri.toString());
                    try {
                        picture = BitmapFactory.decodeStream(
                                activity.getContentResolver().openInputStream(croppedImageUri));
                        ivTakePicture.setImageBitmap(picture);
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, e.toString());
                    }
                    break;

                // 也可取得自行設計登入畫面的帳號密碼
                case REQ_LOGIN:
                    SharedPreferences pref = activity.getSharedPreferences(Common.PREF_FILE,
                            MODE_PRIVATE);
                    if (networkConnected()) {
                        String url = Common.URL + "/DataUploadServlet";
                        String name = pref.getString("user", "");
                        byte[] image = Common.bitmapToPNG(picture);
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("name", name);
                        jsonObject.addProperty("imageBase64", Base64.encodeToString(image, Base64.DEFAULT));
                        //base64是把非純文字轉成文字
                        dataUploadTask = new MyTask(url, jsonObject.toString());
                        try {
                            String jsonIn = dataUploadTask.execute().get();
                            JsonObject jObject = new Gson().fromJson(jsonIn, JsonObject.class);
                            name = jObject.get("name").getAsString();
                            image = Base64.decode(jObject.get("imageBase64").getAsString(), Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);

                            String text = "name: " + name;
                            tvResultUser.setText(text);
                            ivResultImage.setImageBitmap(bitmap);
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    } else {
                        showToast(activity, R.string.textNoNetwork);
                    }
                    break;
            }
        }
    }

    private void crop(Uri sourceImageUri) {
        File file = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        file = new File(file, "picture_cropped.jpg");
        croppedImageUri = Uri.fromFile(file);
        // take care of exceptions
        try {
            // call the standard crop action intent (the user device may not support it)
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            // the recipient of this Intent can read soruceImageUri's data
            cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // set image source Uri and type
            cropIntent.setDataAndType(sourceImageUri, "image/*");
            // send crop message
            cropIntent.putExtra("crop", "true");
            // aspect ratio of the cropped area, 0 means user define
            cropIntent.putExtra("aspectX", 0); // this sets the max width
            cropIntent.putExtra("aspectY", 0); // this sets the max height
            // output with and height, 0 keeps original size
            cropIntent.putExtra("outputX", 0);
            cropIntent.putExtra("outputY", 0);
            // whether keep original aspect ratio
            cropIntent.putExtra("scale", true);//縮放
            cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, croppedImageUri);
            // whether return data by the intent
            cropIntent.putExtra("return-data", true);
            // start the activity - we handle returning in onActivityResult
            startActivityForResult(cropIntent, REQ_CROP_PICTURE);//請求
        }
        // respond to users whose devices do not support the crop action
        catch (ActivityNotFoundException anfe) {
            Toast.makeText(activity, "This device doesn't support the crop action!", Toast.LENGTH_SHORT).show();
        }
    }

    // check if the device connect to the network
    private boolean networkConnected() {
        ConnectivityManager conManager =
                (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = conManager != null ? conManager.getActiveNetworkInfo() : null;
        return networkInfo != null && networkInfo.isConnected();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (dataUploadTask != null) {
            dataUploadTask.cancel(true);
        }
    }

    private void showToast(Context context, int messageId) {
        Toast.makeText(context, messageId, Toast.LENGTH_SHORT).show();
    }
}
