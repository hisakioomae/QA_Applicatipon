package jp.app.oomae.hisaki.qa_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by hisaki on 2017/06/26.
 * Preferenceから表示名を取得してEditTextに反映させる
 * 表示名変更ボタンとログアウトボタンのOnClickListenerを設定
 */

public class SettingActivity extends AppCompatActivity {

    DatabaseReference mDataBaseReference;
    private EditText nameText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        // Preferenceから表示名を取得してEditTextに反映させる
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String name = sp.getString(Const.NameKEY, "");
        nameText = (EditText) findViewById(R.id.nameText);
        nameText.setText(name);

        mDataBaseReference = FirebaseDatabase.getInstance().getReference();

        // UIの初期設定
        setTitle("設定");
        Button changeButton = (Button) findViewById(R.id.changeButton);
        changeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // キーボードが出ていたら閉じる
                InputMethodManager im = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                im.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

                // ログイン済みのユーザーを収録する
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                if (user == null) {// ログインしていない場合
                    Snackbar.make(v, "ログインしていません", Snackbar.LENGTH_LONG).show();
                    return;
                }

                // 変更した表示名をFirebaseに保存する
                String name = nameText.getText().toString();
                DatabaseReference userRef = mDataBaseReference.child(Const.UsersPATH).child(user.getUid());
                Map<String, String> data = new HashMap<>();
                data.put("name", name);
                userRef.setValue(data);

                // 変更した表示名をPreferenceに保存する(アプリの設定データをデバイス内に保存するための仕組み)
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = sp.edit();
                editor.putString(Const.NameKEY, name);
                editor.commit();

                Snackbar.make(v, "表示名を変更しました", Snackbar.LENGTH_LONG).show();
            }
        });

        Button logoutButton = (Button) findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                nameText.setText("");
                Snackbar.make(v, "ログアウトしました", Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
