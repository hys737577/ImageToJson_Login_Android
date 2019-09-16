package idv.ron.imagetojson_login_android;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/* AndroidManifest.xml加上android:theme="@style/Theme.AppCompat.Dialog"
    此Activity會以對話視窗模式顯示。呼叫setResult()設定回傳結果 */
public class LoginDialogActivity extends AppCompatActivity {
    private static final String TAG = "LoginDialogActivity";
    private EditText etUser;
    private EditText etPassword;
    private TextView tvMessage;
    private MyTask loginTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);
        handleViews();
        setResult(RESULT_CANCELED);//社ok的話使用者按掉還是會登入
    }

    private void handleViews() {
        etUser = findViewById(R.id.etUser);
        etPassword = findViewById(R.id.etPassword);
        Button btLogin = findViewById(R.id.btLogin);
        tvMessage = findViewById(R.id.tvMessage);

        btLogin.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                String user = etUser.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                if (user.length() <= 0 || password.length() <= 0) {
                    showMessage(R.string.textInvalidUserOrPassword);
                    return;
                }
                       //偏好設定檔
                if (isUserValid(user, password)) {
                    SharedPreferences pref = getSharedPreferences(Common.PREF_FILE,
                            MODE_PRIVATE);
                    pref.edit()
                            .putBoolean("login", true)
                            .putString("user", user)
                            .putString("password", password)
                            .apply();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    showMessage(R.string.textInvalidUserOrPassword);
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        setTitle(R.string.textLogin);
        SharedPreferences pref = getSharedPreferences(Common.PREF_FILE, MODE_PRIVATE);
        boolean login = pref.getBoolean("login", false);
        if (login) {
            String name = pref.getString("user", "");
            String password = pref.getString("password", "");
            if (isUserValid(name, password)) {
                setResult(RESULT_OK);
                finish();
            } else {
                pref.edit().putBoolean("login", false).apply();
                showMessage(R.string.textInvalidUserOrPassword);
            }
        }
    }

    private void showMessage(int msgResId) {
        tvMessage.setText(msgResId);
    }

    private boolean isUserValid(String name, String password) {
        String url = Common.URL + "/LoginServlet";
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("name", name);
        jsonObject.addProperty("password", password);
        loginTask = new MyTask(url, jsonObject.toString());
        boolean isUserValid = false;
        try {
            String jsonIn = loginTask.execute().get();
            jsonObject = new Gson().fromJson(jsonIn, JsonObject.class);
            isUserValid = jsonObject.get("isUserValid").getAsBoolean();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return isUserValid;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (loginTask != null) {
            loginTask.cancel(true);
        }
    }
}
