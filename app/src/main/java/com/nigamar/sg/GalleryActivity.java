package com.nigamar.sg;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.SharedPreferencesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GalleryActivity extends AppCompatActivity implements GalleryAdapter.onImageClicked{

    private static final int CODE_READ_EXTERNAL_STORAGE = 0;

    private Toolbar toolbar;

    private RecyclerView mRecyclerView;

    private GalleryAdapter mGalleryAdapter;

    private GridLayoutManager mLayoutManager;

    private TextView defaultMessageTextView;

    private SharedPreferences mSharedPreferences;

    // the root reference
    private StorageReference mStorageReference,mChildStorageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mSharedPreferences=getSharedPreferences(CONSTANTS.PREF_FILE_NAME,MODE_PRIVATE);
        mRecyclerView= (RecyclerView) findViewById(R.id.galleryRecyclerView);
        defaultMessageTextView= (TextView) findViewById(R.id.defaultTextView);
        mLayoutManager=new GridLayoutManager(this,2);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mStorageReference=FirebaseStorage.getInstance().getReference();
        mChildStorageReference=mStorageReference.child("media");
        checkExternalReadStoragePermission();
        setGalleryAdapter();
    }

    private void setGalleryAdapter() {
        List<File> galleryList=getGallery();
        if (galleryList!=null && galleryList.size() > 0){
            mGalleryAdapter=new GalleryAdapter(this,galleryList,this);
            mRecyclerView.setAdapter(mGalleryAdapter);
        }
        else {
            defaultMessageTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case CODE_READ_EXTERNAL_STORAGE:
                if (grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                    finish();
                }
                else {
                    setGalleryAdapter();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void checkExternalReadStoragePermission(){
        if (Build.VERSION.SDK_INT >=23){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED){
                setGalleryAdapter();
            }
            else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)){
                    Toast.makeText(this,"this permission is needed to view the gallery of all the selfies taken",
                            Toast.LENGTH_LONG).show();
                }
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},CODE_READ_EXTERNAL_STORAGE);
            }
        }
        else {
            setGalleryAdapter();
        }
    }

    private List<File> getGallery(){
        List<File> galleryList=new ArrayList<>();
        File rootDirectory=new File(Environment.getExternalStorageDirectory(),CONSTANTS.DIRECTORY_NAME);
        File videosDirectory= rootDirectory.listFiles()[0];
        File imageDirectory=rootDirectory.listFiles()[1];
        galleryList.addAll(Arrays.asList(imageDirectory.listFiles()));
        galleryList.addAll(Arrays.asList(videosDirectory.listFiles()));
        return galleryList;
    }

    @Override
    protected void onDestroy() {
        mGalleryAdapter=null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void uploadMedia(final File file, int position) {
        if(!isUploaded(file)) {
            // get the view holder for the clicked position
            final GalleryViewHolder holder = (GalleryViewHolder) mRecyclerView.findViewHolderForAdapterPosition(position);
            showUploadProgress(holder);
            hideUploadIndicator(holder);
            Uri uri = Uri.parse("file://" + file.getAbsolutePath());

            UploadTask imageUploadTask = mChildStorageReference.child(file.getName()).putFile(uri);
            imageUploadTask.addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    hideUploadProgress(holder);
                    markFileAsUploaded(file);
                    updateUploadIndicator(holder);
                }
            }).addOnFailureListener(this, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // show user the error
                    Toast.makeText(getApplicationContext(), "Sorry! unable to upload media", Toast.LENGTH_LONG).show();
                    showUploadIndicator(holder);
                }
            }).addOnProgressListener(this, new OnProgressListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                    double percentage = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                    updateUploadProgress(holder, percentage);
                }
            });
        }
        else {
            Toast.makeText(this,"File is already uploaded",Toast.LENGTH_LONG).show();
        }
    }

    private boolean isUploaded(File file) {
        return mSharedPreferences.getBoolean(file.getName(),false);
    }

    private void updateUploadProgress(GalleryViewHolder holder, double percentage) {
        holder.getUploadProgress().setText("Uploading.. "+(int)percentage+" % ");
    }

    @SuppressLint("NewApi")
    private void updateUploadIndicator(GalleryViewHolder holder) {
        holder.getUploadIndicator().setImageDrawable(
                getResources().getDrawable(R.drawable.ic_check_circle_black_24dp)
        );
        holder.getUploadIndicator().setImageTintList(ColorStateList.valueOf(Color.GREEN));
        showUploadIndicator(holder);
    }

    private void showUploadIndicator(GalleryViewHolder holder) {
        holder.getUploadIndicator().setVisibility(View.VISIBLE);
    }

    private void markFileAsUploaded(File file) {
        SharedPreferences.Editor editor=mSharedPreferences.edit();
        editor.putBoolean(file.getName(),CONSTANTS.IS_UPLOADED);
        editor.commit();
    }

    private void hideUploadProgress(GalleryViewHolder holder) {
        holder.getUploadProgress().setVisibility(View.INVISIBLE);
    }

    private void showUploadProgress(GalleryViewHolder holder) {
        holder.getUploadProgress().setVisibility(View.VISIBLE);
    }

    private void hideUploadIndicator(GalleryViewHolder holder){
      holder.getUploadIndicator().setVisibility(View.INVISIBLE);
    }
}
