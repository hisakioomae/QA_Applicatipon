package jp.app.oomae.hisaki.qa_app;

/**
 * Created by hisaki on 2017/06/26.
 * onCreate:渡ってきたジャンルの番号を保持。UIの準備。
 * onActivityResult:Intent連携で取得した画像をリサイズしてImageViewに設定。
 * onClick:ImageViewとButtonがタップされた時の処理。ImageViewをタップしたときは必要であれば許可を求めるダイアログを表示。
 * onRequestPermissionsResult:許可を求めるダイアログからの結果を受け取る。
 * showChooser:Intent連携の選択ダイアログを表示する。
 * onComplete:Firebaseへの保存完了時に呼ばれる。
 */

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class QuestionSendActivity extends AppCompatActivity implements View.OnClickListener, DatabaseReference.CompletionListener {

    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final int CHOOSER_REQUEST_CODE = 100;

    private ProgressDialog mProgress;
    private EditText mTitleText;
    private EditText mBodyText;
    private ImageView mImageView;
    private Button mSendButton;

    private int mGenre;// ジャンルを保持する
    private Uri mPictureUri; // カメラで撮影した画像を保存するURI

    /**
     * Intentで渡ってきたジャンル番号を取り出してmGenreで保持
     * タイトルの設定
     * UIをメンバ変数として保持
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question_send);

        // 渡ってきたジャンルの番号を保持する
        Bundle extras = getIntent().getExtras();
        mGenre = extras.getInt("genre");

        // UIの準備
        setTitle("質問作成");

        mTitleText = (EditText) findViewById(R.id.titleText);
        mBodyText = (EditText) findViewById(R.id.bodyText);

        mSendButton = (Button) findViewById(R.id.sendButton);
        mSendButton.setOnClickListener(this);

        mImageView = (ImageView) findViewById(R.id.imageView);
        mImageView.setOnClickListener(this);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("投稿中...");

    }

    /**
     * Intent連携から戻ってきた時に画像を取得
     * ImageViewに設定
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHOOSER_REQUEST_CODE) {

            if (resultCode != RESULT_OK) {
                if (mPictureUri != null) {
                    getContentResolver().delete(mPictureUri, null, null);
                    mPictureUri = null;
                }
                return;
            }

            // 画像を取得
            Uri uri;
            if (data == null) {
                uri = mPictureUri;
            } else if (data.getData() == null) {
                uri = mPictureUri;
            } else {
                uri = data.getData();//data.getData()で取得出来た場合はそのURIを使用
            }

            // URIからBitmapを取得する
            Bitmap image;
            try {
                ContentResolver contentResolver = getContentResolver();
                InputStream inputStream = contentResolver.openInputStream(uri);
                image = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
            } catch (Exception e) {
                return;
            }

            // 取得したBimapの長辺を500ピクセルにリサイズする
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            float scale = Math.min((float) 500 / imageWidth, (float) 500 / imageHeight); // (1)

            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);

            Bitmap resizedImage =  Bitmap.createBitmap(image, 0, 0, imageWidth, imageHeight, matrix, true);

            // BitmapをImageViewに設定する
            mImageView.setImageBitmap(resizedImage);

            mPictureUri = null;
        }
    }

    /**
     * 添付画像を選択・表示するImageViewをタップした時の処理
     * 投稿ボタンをタップした時の処理
     */
    @Override
    public void onClick(View v) {
        if (v == mImageView) {//添付画像を選択・表示するImageViewをタップした時の処理
            // パーミッションの許可状態を確認する
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    // 許可されている
                    showChooser();
                } else {
                    // 許可されていないので許可ダイアログを表示する
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);

                    return;
                }
            } else {
                showChooser();
            }
        } else if (v == mSendButton) {//投稿ボタンをタップした時の処理
            // キーボードが出てたら閉じる
            InputMethodManager im = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

            DatabaseReference dataBaseReference = FirebaseDatabase.getInstance().getReference();
            DatabaseReference genreRef = dataBaseReference.child(Const.ContentsPATH).child(String.valueOf(mGenre));

            Map<String, String> data = new HashMap<>();

            // UID
            data.put("uid", FirebaseAuth.getInstance().getCurrentUser().getUid());

            // タイトルと本文を取得する
            String title = mTitleText.getText().toString();
            String body = mBodyText.getText().toString();

            if (title.length() == 0) {
                // 質問が入力されていない時はエラーを表示するだけ
                Snackbar.make(v, "タイトルを入力して下さい", Snackbar.LENGTH_LONG).show();
                return;
            }

            if (body.length() == 0) {
                // 質問が入力されていない時はエラーを表示するだけ
                Snackbar.make(v, "質問を入力して下さい", Snackbar.LENGTH_LONG).show();
                return;
            }

            // Preferenceから名前を取る
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            String name = sp.getString(Const.NameKEY, "");

            data.put("title", title);
            data.put("body", body);
            data.put("name", name);

            // 添付画像を取得する
            BitmapDrawable drawable = (BitmapDrawable) mImageView.getDrawable();

            // 添付画像が設定されていれば画像を取り出してBASE64エンコードする
            if (drawable != null) {
                Bitmap bitmap = drawable.getBitmap();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                String bitmapString = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                data.put("image", bitmapString);
            }

            genreRef.push().setValue(data, this);
            mProgress.show();
        }
    }

    /**
     * 許可ダイアログでユーザが選択した結果を受けとる
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // ユーザーが許可したとき
                    showChooser();
                }
                return;
            }
        }
    }

    /**
     * ギャラリー選択とカメラ選択
     */
    private void showChooser() {
        // ギャラリーから選択するIntent
        Intent garallyIntent = new Intent(Intent.ACTION_GET_CONTENT);
        garallyIntent.setType("image/*");
        garallyIntent.addCategory(Intent.CATEGORY_OPENABLE);

        // カメラで撮影するIntent
        String filename = System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, filename);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        mPictureUri = getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mPictureUri);

        // ギャラリー選択のIntentを与えてcreateChooserメソッドを呼ぶ
        Intent chooserIntent = Intent.createChooser(garallyIntent, "画像を取得する");
        // EXTRA_INITIAL_INTENTS にカメラ撮影のIntentを追加
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});

        startActivityForResult(chooserIntent, CHOOSER_REQUEST_CODE);//(startActivityForResult)必ず画面の遷移先から元の画面に戻って来る場合に用いられる
    }

    /**
     * Firebaseへの保存が完了したらfinishメソッドを呼び出してActivityを閉じます
     */
    @Override
    public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
        mProgress.dismiss();

        if (databaseError == null) {
            finish();
        } else {
            Snackbar.make(findViewById(android.R.id.content), "投稿に失敗しました", Snackbar.LENGTH_LONG).show();
        }
    }
}