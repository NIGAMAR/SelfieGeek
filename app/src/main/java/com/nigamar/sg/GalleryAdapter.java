package com.nigamar.sg;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.github.florent37.glidepalette.BitmapPalette;
import com.github.florent37.glidepalette.GlidePalette;

import java.io.File;
import java.util.List;

/**
 * Created by Appy on 03/10/16.
 */

public class GalleryAdapter extends RecyclerView.Adapter<GalleryViewHolder> {

    private View galleryView;

    private Activity mActivity;

    private List<File> galleryList;

    private onImageClicked mListener;

    private SharedPreferences mSharedPreferences;

    public interface onImageClicked{
        void uploadMedia(File file,int position);
    }

    public GalleryAdapter(Activity activity, List<File> galleryList,onImageClicked onImageClicked) {
        this.mListener=onImageClicked;
        this.mActivity=activity;
        this.galleryList=galleryList;
        mSharedPreferences=mActivity.getSharedPreferences(CONSTANTS.PREF_FILE_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public GalleryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        galleryView= LayoutInflater.from(parent.getContext()).inflate(R.layout.single_gallery_item,parent,false);
        return new GalleryViewHolder(galleryView);
    }

    @Override
    public void onBindViewHolder(GalleryViewHolder holder, final int position) {
        final File file=galleryList.get(position);
        updateUi(holder, file);
        if (file.getName().contains("VIDEO_")){
            holder.getPlayButton().setVisibility(View.VISIBLE);
        }
        if (isUploaded(file)){
            updateUploadIndicator(holder);
        }
        holder.getUploadIndicator().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.uploadMedia(file,position);
            }
        });
    }

    private boolean isUploaded(File file) {
        return mSharedPreferences.getBoolean(file.getName(),false);
    }

    @SuppressLint("NewApi")
    private void updateUploadIndicator(GalleryViewHolder holder) {
        holder.getUploadIndicator().setImageDrawable(
                mActivity.getResources().getDrawable(R.drawable.ic_check_circle_black_24dp)
        );
        holder.getUploadIndicator().setImageTintList(ColorStateList.valueOf(Color.GREEN));
    }

    private void updateUi(GalleryViewHolder holder, File file) {
        Uri mediaUri=Uri.parse("file://"+file.getAbsolutePath());
        Glide.with(mActivity)
               .load(mediaUri)
                .listener(
                        GlidePalette.with(mediaUri.toString())
                                .use(GlidePalette.Profile.MUTED_DARK).
                                intoBackground(holder.getUploadProgress())
                )
                .into(holder.getmImageView());
    }

    @Override
    public int getItemCount() {
         return (galleryList!=null) ? galleryList.size():0 ;
    }

}
class GalleryViewHolder extends RecyclerView.ViewHolder {

    private ImageView mImageView,uploadIndicator;
    private ImageButton playButton;
    private TextView uploadProgress;

    public GalleryViewHolder(View itemView) {
        super(itemView);
        mImageView= (ImageView) itemView.findViewById(R.id.galleryImage);
        playButton= (ImageButton) itemView.findViewById(R.id.playButton);
        uploadProgress= (TextView) itemView.findViewById(R.id.uploadProgress);
        uploadIndicator= (ImageView) itemView.findViewById(R.id.uploadIndicator);
    }

    public ImageView getmImageView() {
        return mImageView;
    }

    public ImageButton getPlayButton() {
        return playButton;
    }

    public TextView getUploadProgress() {
        return uploadProgress;
    }

    public ImageView getUploadIndicator() {
        return uploadIndicator;
    }
}
