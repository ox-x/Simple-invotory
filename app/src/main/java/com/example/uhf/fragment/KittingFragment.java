package com.example.uhf.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.db.BoxInfo;
import com.example.uhf.db.ContentInfo;
import com.example.uhf.db.DatabaseHelper;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Kitting Fragment: Assign Parent (Box) and Child (Content) RFID tags.
 * Supports camera photo capture for each item.
 */
public class KittingFragment extends KeyDwonFragment {

    private static final String TAG = "KittingFragment";
    private static final int REQUEST_BOX_PHOTO = 100;
    private static final int REQUEST_CONTENT_PHOTO = 101;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private UHFMainActivity mContext;
    private DatabaseHelper dbHelper;

    // Box section
    private EditText etBoxEpc, etBoxShortId, etBoxDescription;
    private Button btnScanBox, btnAssignBox, btnBoxPhoto;
    private ImageView ivBoxPhoto;

    // Content section
    private EditText etContentEpc, etContentShortId, etContentDescription;
    private Button btnScanContent, btnAssignContent, btnContentPhoto;
    private ImageView ivContentPhoto;

    // Contents list
    private ListView lvContents;
    private Button btnClearKitting;
    private ContentsAdapter contentsAdapter;
    private List<ContentInfo> contentsList = new ArrayList<>();

    // Photo tracking
    private String currentBoxPhotoPath = "";
    private String currentContentPhotoPath = "";
    private String currentPhotoPath; // Temp path for camera capture

    private static final String KEY_CURRENT_PHOTO_PATH = "current_photo_path";
    private static final String KEY_BOX_PHOTO_PATH = "box_photo_path";
    private static final String KEY_CONTENT_PHOTO_PATH = "content_photo_path";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentPhotoPath != null) outState.putString(KEY_CURRENT_PHOTO_PATH, currentPhotoPath);
        if (currentBoxPhotoPath != null) outState.putString(KEY_BOX_PHOTO_PATH, currentBoxPhotoPath);
        if (currentContentPhotoPath != null) outState.putString(KEY_CONTENT_PHOTO_PATH, currentContentPhotoPath);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kitting, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        dbHelper = DatabaseHelper.getInstance(mContext);

        // Restore photo paths after configuration change
        if (savedInstanceState != null) {
            currentPhotoPath = savedInstanceState.getString(KEY_CURRENT_PHOTO_PATH);
            currentBoxPhotoPath = savedInstanceState.getString(KEY_BOX_PHOTO_PATH, "");
            currentContentPhotoPath = savedInstanceState.getString(KEY_CONTENT_PHOTO_PATH, "");
        }

        initViews();
    }

    private void initViews() {
        View v = getView();

        // Box section
        etBoxEpc = v.findViewById(R.id.etBoxEpc);
        etBoxShortId = v.findViewById(R.id.etBoxShortId);
        etBoxDescription = v.findViewById(R.id.etBoxDescription);
        btnScanBox = v.findViewById(R.id.btnScanBox);
        btnAssignBox = v.findViewById(R.id.btnAssignBox);
        btnBoxPhoto = v.findViewById(R.id.btnBoxPhoto);
        ivBoxPhoto = v.findViewById(R.id.ivBoxPhoto);

        // Content section
        etContentEpc = v.findViewById(R.id.etContentEpc);
        etContentShortId = v.findViewById(R.id.etContentShortId);
        etContentDescription = v.findViewById(R.id.etContentDescription);
        btnScanContent = v.findViewById(R.id.btnScanContent);
        btnAssignContent = v.findViewById(R.id.btnAssignContent);
        btnContentPhoto = v.findViewById(R.id.btnContentPhoto);
        ivContentPhoto = v.findViewById(R.id.ivContentPhoto);

        // List
        lvContents = v.findViewById(R.id.lvContents);
        btnClearKitting = v.findViewById(R.id.btnClearKitting);

        contentsAdapter = new ContentsAdapter();
        lvContents.setAdapter(contentsAdapter);

        // Listeners
        btnScanBox.setOnClickListener(view -> scanBoxTag());
        btnAssignBox.setOnClickListener(view -> assignBox());
        btnBoxPhoto.setOnClickListener(view -> takePhoto(REQUEST_BOX_PHOTO));

        btnScanContent.setOnClickListener(view -> scanContentTag());
        btnAssignContent.setOnClickListener(view -> assignContent());
        btnContentPhoto.setOnClickListener(view -> takePhoto(REQUEST_CONTENT_PHOTO));

        btnClearKitting.setOnClickListener(view -> clearKitting());
    }

    private void scanBoxTag() {
        UHFTAGInfo tag = mContext.mReader.inventorySingleTag();
        if (tag != null) {
            String tid = getTagId(tag);
            etBoxEpc.setText(tid);
            mContext.playSound(1);

            // Load existing box data if exists
            BoxInfo existing = dbHelper.getBoxByEpc(tid);
            if (existing != null) {
                etBoxShortId.setText(existing.shortId);
                etBoxDescription.setText(existing.description);
                currentBoxPhotoPath = existing.photoPath;
                if (currentBoxPhotoPath != null && !currentBoxPhotoPath.isEmpty()) {
                    loadPhotoToView(currentBoxPhotoPath, ivBoxPhoto);
                }
                // Load contents for this box
                loadContentsForBox(tid);
            }
        } else {
            mContext.showToast(R.string.uhf_msg_inventory_fail);
        }
    }

    private void scanContentTag() {
        UHFTAGInfo tag = mContext.mReader.inventorySingleTag();
        if (tag != null) {
            etContentEpc.setText(getTagId(tag));
            mContext.playSound(1);
        } else {
            mContext.showToast(R.string.uhf_msg_inventory_fail);
        }
    }

    private void assignBox() {
        String epc = etBoxEpc.getText().toString().trim();
        if (epc.isEmpty()) {
            mContext.showToast(R.string.kitting_no_box);
            return;
        }
        String shortId = etBoxShortId.getText().toString().trim();
        String description = etBoxDescription.getText().toString().trim();

        long result = dbHelper.insertBox(epc, shortId, description, currentBoxPhotoPath);
        if (result != -1) {
            mContext.showToast(getString(R.string.kitting_box_assigned, shortId.isEmpty() ? epc : shortId));
            loadContentsForBox(epc);
        } else {
            mContext.showToast(R.string.kitting_saved_fail);
        }
    }

    private void assignContent() {
        String boxEpc = etBoxEpc.getText().toString().trim();
        if (boxEpc.isEmpty()) {
            mContext.showToast(R.string.kitting_no_box);
            return;
        }

        String contentEpc = etContentEpc.getText().toString().trim();
        if (contentEpc.isEmpty()) {
            mContext.showToast("Please scan a content tag first");
            return;
        }

        // Ensure box exists in DB
        BoxInfo box = dbHelper.getBoxByEpc(boxEpc);
        if (box == null) {
            // Auto-create the box
            dbHelper.insertBox(boxEpc, etBoxShortId.getText().toString().trim(),
                    etBoxDescription.getText().toString().trim(), currentBoxPhotoPath);
        }

        String shortId = etContentShortId.getText().toString().trim();
        String description = etContentDescription.getText().toString().trim();

        // Check for duplicate
        if (dbHelper.contentExists(boxEpc, contentEpc)) {
            mContext.showToast("This content tag is already assigned to this box");
            return;
        }

        long result = dbHelper.insertContent(boxEpc, contentEpc, shortId, description, currentContentPhotoPath);
        if (result != -1) {
            mContext.showToast(getString(R.string.kitting_content_added, shortId.isEmpty() ? contentEpc : shortId));
            // Clear content fields
            etContentEpc.setText("");
            etContentShortId.setText("");
            etContentDescription.setText("");
            currentContentPhotoPath = "";
            ivContentPhoto.setVisibility(View.GONE);
            // Reload list
            loadContentsForBox(boxEpc);
        } else {
            mContext.showToast(R.string.kitting_saved_fail);
        }
    }

    private void loadContentsForBox(String boxEpc) {
        contentsList.clear();
        contentsList.addAll(dbHelper.getContentsByBoxEpc(boxEpc));
        contentsAdapter.notifyDataSetChanged();
    }

    private void clearKitting() {
        etBoxEpc.setText("");
        etBoxShortId.setText("");
        etBoxDescription.setText("");
        currentBoxPhotoPath = "";
        ivBoxPhoto.setVisibility(View.GONE);

        etContentEpc.setText("");
        etContentShortId.setText("");
        etContentDescription.setText("");
        currentContentPhotoPath = "";
        ivContentPhoto.setVisibility(View.GONE);

        contentsList.clear();
        contentsAdapter.notifyDataSetChanged();
    }

    // ==================== Camera / Photo ====================

    private int pendingPhotoRequest = -1; // tracks which photo request triggered permission

    private void takePhoto(int requestCode) {
        // Check runtime camera permission first (required on Android 6+)
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            pendingPhotoRequest = requestCode;
            ActivityCompat.requestPermissions(mContext,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return;
        }
        launchCamera(requestCode);
    }

    private void launchCamera(int requestCode) {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photoFile = createImageFile();
            currentPhotoPath = photoFile.getAbsolutePath();
            Uri photoUri = FileProvider.getUriForFile(mContext,
                    mContext.getPackageName() + ".fileprovider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // Start camera even if resolveActivity returns null (Android 11+ fallback)
            startActivityForResult(takePictureIntent, requestCode);
        } catch (Exception ex) {
            Log.e(TAG, "Error launching camera", ex);
            Toast.makeText(mContext, "Camera error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, launch the pending photo request
                if (pendingPhotoRequest != -1) {
                    launchCamera(pendingPhotoRequest);
                    pendingPhotoRequest = -1;
                }
            } else {
                Toast.makeText(mContext, "Camera permission denied", Toast.LENGTH_SHORT).show();
                pendingPhotoRequest = -1;
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "UHF_" + timeStamp + "_";
        // Use app cache dir - works on all Android versions without storage permission
        File storageDir = new File(mContext.getCacheDir(), "UHF_photos");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && currentPhotoPath != null) {
            if (requestCode == REQUEST_BOX_PHOTO) {
                currentBoxPhotoPath = currentPhotoPath;
                loadPhotoToView(currentPhotoPath, ivBoxPhoto);
                mContext.showToast(R.string.kitting_photo_saved);
            } else if (requestCode == REQUEST_CONTENT_PHOTO) {
                currentContentPhotoPath = currentPhotoPath;
                loadPhotoToView(currentPhotoPath, ivContentPhoto);
                mContext.showToast(R.string.kitting_photo_saved);
            }
        }
    }

    private void loadPhotoToView(String path, ImageView iv) {
        if (path == null || path.isEmpty()) return;
        File f = new File(path);
        if (!f.exists()) return;
        try {
            // Decode with sample size to avoid OOM on large photos
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            int targetSize = 400; // target max dimension in pixels
            int sampleSize = 1;
            while (options.outWidth / sampleSize > targetSize && options.outHeight / sampleSize > targetSize) {
                sampleSize *= 2;
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            Bitmap bmp = BitmapFactory.decodeFile(path, options);
            if (bmp != null) {
                iv.setImageBitmap(bmp);
                iv.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading photo", e);
        }
    }

    @Override
    public void myOnKeyDwon() {
        // Hardware scan button: scan into box if empty, else scan into content
        UHFTAGInfo tag = mContext.mReader.inventorySingleTag();
        if (tag != null) {
            mContext.playSound(1);
            String tid = getTagId(tag);
            if (etBoxEpc.getText().toString().trim().isEmpty()) {
                etBoxEpc.setText(tid);
            } else {
                etContentEpc.setText(tid);
            }
        }
    }

    /**
     * Get unique tag identifier: prefer TID, fallback to EPC.
     */
    private String getTagId(UHFTAGInfo tag) {
        String tid = tag.getTid();
        if (tid != null && !tid.isEmpty() && !tid.equals("000000000000000000000000")) {
            return tid;
        }
        return tag.getEPC();
    }

    // ==================== Contents List Adapter ====================

    private class ContentsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return contentsList.size();
        }

        @Override
        public Object getItem(int position) {
            return contentsList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.item_kitting_content, parent, false);
                holder = new ViewHolder();
                holder.ivPhoto = convertView.findViewById(R.id.ivItemPhoto);
                holder.tvName = convertView.findViewById(R.id.tvItemName);
                holder.tvEpc = convertView.findViewById(R.id.tvItemEpc);
                holder.tvDesc = convertView.findViewById(R.id.tvItemDescription);
                holder.btnDelete = convertView.findViewById(R.id.btnDeleteItem);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ContentInfo item = contentsList.get(position);
            holder.tvName.setText(item.shortId != null && !item.shortId.isEmpty() ? item.shortId : "Item " + (position + 1));
            holder.tvEpc.setText("TID: " + item.epc);
            holder.tvDesc.setText(item.description != null ? item.description : "");

            // Load photo thumbnail
            if (item.photoPath != null && !item.photoPath.isEmpty()) {
                loadPhotoToView(item.photoPath, holder.ivPhoto);
            } else {
                holder.ivPhoto.setImageDrawable(null);
                holder.ivPhoto.setBackgroundColor(0xFFEEEEEE);
            }

            // Delete button
            final int pos = position;
            holder.btnDelete.setOnClickListener(v -> {
                ContentInfo toDelete = contentsList.get(pos);
                dbHelper.deleteContent(toDelete.id);
                contentsList.remove(pos);
                notifyDataSetChanged();
            });

            return convertView;
        }

        class ViewHolder {
            ImageView ivPhoto;
            TextView tvName, tvEpc, tvDesc;
            Button btnDelete;
        }
    }
}
