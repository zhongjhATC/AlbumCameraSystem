package com.zhongjh.albumcamerasystem;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;
import top.zibin.luban.Luban;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    ViewHolder mViewHolder;

    public final static int FILECHOOSER_RESULTCODE = 1000; // 表单的结果回调
    public static final int REQ_CAMERA = FILECHOOSER_RESULTCODE + 1; // 拍照
    public final static int REQ_VIDEO = REQ_CAMERA + 1; // 录像
    public final static int REQ_PHOTO = REQ_VIDEO + 1; // 相册
    private Uri imageUri;
    private File imageFile;

    private CompositeDisposable mDisposable = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mViewHolder = new ViewHolder(MainActivity.this);
        // 权限请求
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            mViewHolder.tvPermission.setText("无权限");
        } else {
            init();
        }
        initListener();
    }

    /**
     * NeedsPermission 注解在需要调用运行时权限的方法上，当用户给予权限时会执行该方法
     */
    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE})
    public void needsPermission() {
        // 申请成功，进行相应操作
        init();
    }

    /**
     * 注解在用于向用户解释为什么需要调用该权限的方法上，只有当第一次请求权限被用户拒绝，下次请求权限之前会调用
     */
    @OnShowRationale({Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE})
    public void permissionWriteExternalStorageRationale(PermissionRequest request) {
        // 再次请求
        new AlertDialog.Builder(MainActivity.this)
                .setMessage("说明：你必须给权限才能正常运行")
                .setPositiveButton("立即允许", (dialog, which) -> request.proceed())
                .show();
    }

    /**
     * OnPermissionDenied 注解在当用户拒绝了权限请求时需要调用的方法上
     */
    @OnPermissionDenied({Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE})
    public void permissionWriteExternalStorageDenied() {
        // 再次请求
        MainActivityPermissionsDispatcher.needsPermissionWithPermissionCheck(MainActivity.this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    /**
     * 初始化
     */
    private void init() {
        mViewHolder.tvPermission.setText("有权限");
    }

    /**
     * 初始化事件
     */
    private void initListener() {
        // 请求权限
        mViewHolder.btnPermission.setOnClickListener(view -> MainActivityPermissionsDispatcher.needsPermissionWithPermissionCheck(MainActivity.this));
        // 拍照
        mViewHolder.btnPhoto.setOnClickListener(view -> {
            if (mViewHolder.tvPermission.getText().toString().equals("无权限"))
                return;

            Intent intentCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String path = getExternalFilesDir(Environment.DIRECTORY_PICTURES) + File.separator + "AlbumCameraSystem" + File.separator;
                imageFile = new File(path, SystemClock.currentThreadTimeMillis() + ".jpg");
                if (!imageFile.getParentFile().exists()) {
                    imageFile.getParentFile().mkdirs();
                }
                imageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileProvider", imageFile);
                // 添加这一句表示对目标应用临时授权该Uri所代表的文件
                intentCamera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intentCamera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } else {
                imageFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) + File.separator + "AlbumCameraSystem" + File.separator,
                        System.currentTimeMillis() + ".jpg");
                if (!imageFile.getParentFile().exists()) {
                    imageFile.getParentFile().mkdirs();
                }
                imageUri = Uri.fromFile(imageFile);
            }
            //将拍照结果保存至 photo_file 的 Uri 中，不保留在相册中
            intentCamera.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(intentCamera, REQ_CAMERA);
        });
        // 打开相册
        mViewHolder.btnAlbum.setOnClickListener(view -> {
            if (mViewHolder.tvPermission.getText().toString().equals("无权限"))
                return;
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQ_PHOTO);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_CAMERA) {
                mDisposable.add(Flowable.just(imageFile)
                        .observeOn(Schedulers.io())
                        .map(imageUri -> Luban.with(MainActivity.this)
                                .load(imageUri)
                                .get())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnError(throwable -> {
                            // 异常
                            Toast.makeText(this, "出现异常", Toast.LENGTH_SHORT).show();
                        })
                        .onErrorResumeNext(Flowable.empty())
                        .subscribe(list -> {
                            for (File file : list) {
                                Glide.with(this).load(file).into(mViewHolder.imgPhoto);
//                                Glide.with(this).load(imageUri).into(mViewHolder.imgPhoto);
                            }
                        }));
            } else if (requestCode == REQ_PHOTO) {
                Uri uri;
                if (data != null) {
                    uri = data.getData();
                } else {
                    return;
                }
//                // 如果不做压缩处理
//                String path = GetImgFromAlbum.getRealPathFromUri(this, uri);
//                Glide.with(this).load(path).into(mViewHolder.imgAlbum);

                // 因为Android10不能直接操作外部文件，所以我们需要拿到bitmap
                Bitmap bitmap = getBitmapFromUri(MainActivity.this, uri);

                // 然后创建一个内部临时图片
                String toPath = getExternalFilesDir(Environment.DIRECTORY_PICTURES) + File.separator + "AlbumCameraSystem" + File.separator;
                File toFile = new File(toPath, "temp.jpg");
                if (!toFile.getParentFile().exists()) {
                    toFile.getParentFile().mkdirs();
                }

                // 存储进bitmap
                saveBitmapFile(bitmap, toFile);

                // 最后根据toFile进行鲁班压缩
                mDisposable.add(Flowable.just(toFile)
                        .observeOn(Schedulers.io())
                        .map(imageFile -> Luban.with(MainActivity.this)
                                .load(imageFile)
                                .get())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnError(throwable -> {
                            // 异常
                            Toast.makeText(this, "出现异常", Toast.LENGTH_SHORT).show();
                        })
                        .onErrorResumeNext(Flowable.empty())
                        .subscribe(list -> {
                            for (File file : list) {
                                Glide.with(this).load(file).into(mViewHolder.imgAlbum);
//                                Glide.with(this).load(uri).into(mViewHolder.imgPhoto);
                            }
                        }));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisposable.clear();
    }

    /**
     * 录像
     */
    private void recordVideo() {
        String path = getExternalFilesDir(Environment.DIRECTORY_MOVIES) + File.separator + "video" + File.separator;
        File fileUri = new File(path, SystemClock.currentThreadTimeMillis() + ".mp4");
        if (!fileUri.getParentFile().exists()) {
            fileUri.getParentFile().mkdirs();
        }
        Uri imageUri = Uri.fromFile(fileUri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            imageUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileProvider", fileUri); // 通过FileProvider创建一个content类型的Uri
        }
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);  // 表示跳转至相机的录视频界面
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0.5);    // MediaStore.EXTRA_VIDEO_QUALITY 表示录制视频的质量，从 0-1，越大表示质量越好，同时视频也越大
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);    // 表示录制完后保存的录制，如果不写，则会保存到默认的路径，在onActivityResult()的回调，通过intent.getData中返回保存的路径
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10);   // 设置视频录制的最长时间
        startActivityForResult(intent, REQ_VIDEO);  // 跳转
    }

    // 通过uri加载图片
    public static Bitmap getBitmapFromUri(Context context, Uri uri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    context.getContentResolver().openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            parcelFileDescriptor.close();
            return image;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveBitmapFile(Bitmap bitmap, File file) {
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static
    class ViewHolder {
        public Button btnPermission;
        public Button btnPhoto;
        public Button btnAlbum;
        public ImageView imgPhoto;
        public ImageView imgAlbum;
        public TextView tvPermission;

        public ViewHolder(MainActivity rootView) {
            this.btnPermission = rootView.findViewById(R.id.btnPermission);
            this.btnPhoto = rootView.findViewById(R.id.btnPhoto);
            this.btnAlbum = rootView.findViewById(R.id.btnAlbum);
            this.imgPhoto = rootView.findViewById(R.id.imgPhoto);
            this.imgAlbum = rootView.findViewById(R.id.imgAlbum);
            this.tvPermission = rootView.findViewById(R.id.tvPermission);
        }

    }
}