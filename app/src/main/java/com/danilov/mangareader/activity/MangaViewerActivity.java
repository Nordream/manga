package com.danilov.mangareader.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.danilov.mangareader.R;
import com.danilov.mangareader.core.application.ApplicationSettings;
import com.danilov.mangareader.core.database.DatabaseAccessException;
import com.danilov.mangareader.core.database.HistoryDAO;
import com.danilov.mangareader.core.database.MangaDAO;
import com.danilov.mangareader.core.interfaces.MangaShowObserver;
import com.danilov.mangareader.core.interfaces.MangaShowStrategy;
import com.danilov.mangareader.core.model.LocalManga;
import com.danilov.mangareader.core.model.Manga;
import com.danilov.mangareader.core.model.MangaChapter;
import com.danilov.mangareader.core.strategy.OfflineManga;
import com.danilov.mangareader.core.strategy.OnlineManga;
import com.danilov.mangareader.core.strategy.ShowMangaException;
import com.danilov.mangareader.core.util.Constants;
import com.danilov.mangareader.core.util.OldPromise;
import com.danilov.mangareader.core.util.Promise;
import com.danilov.mangareader.core.util.ServiceContainer;
import com.danilov.mangareader.core.util.Utils;
import com.danilov.mangareader.core.view.InAndOutAnim;
import com.danilov.mangareader.core.view.MangaImageSwitcher;
import com.danilov.mangareader.core.view.SubsamplingScaleImageView;

import java.util.ArrayList;

/**
 * Created by Semyon Danilov on 06.08.2014.
 */
public class MangaViewerActivity extends BaseToolbarActivity implements MangaShowObserver, MangaShowStrategy.MangaStrategyListener, View.OnClickListener {

    private static final String TAG = "MangaViewerActivity";

    private static final String CURRENT_CHAPTER_KEY = "CCK";
    private static final String CURRENT_IMAGE_KEY = "CIK";
    private static final String CHAPTERS_KEY = "CK";
    private static final String URIS_KEY = "UK";

    private MangaImageSwitcher imageSwitcher;
    private View nextBtn;
    private View prevBtn;
    private View nextBtnBottom;
    private View prevBtnBottom;
    private EditText currentImageEditText;
    private TextView totalImagesTextView;
    private EditText currentChapterEditText;
    private TextView totalChaptersTextView;
    private ProgressBar imageProgressBar;

    private Button imageOk;
    private Button chapterOk;

    private View drawerRightOffsetTop;
    private View drawerRightOffsetBottom;

    private MangaShowStrategy currentStrategy;
    private Manga manga;
    private int fromChapter;
    private int fromPage;

    private ApplicationSettings settings;

    private View tutorialView;

    private DialogFragment progressDialog = null;
    private boolean isFullscreen = false;

    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manga_viewer_activity);
        this.imageSwitcher = (MangaImageSwitcher) findViewById(R.id.imageSwitcher);
        imageSwitcher.setFactory(new SubsamplingImageViewFactory());
        this.nextBtn = findViewById(R.id.nextBtn);
        this.prevBtn = findViewById(R.id.prevBtn);
        this.nextBtnBottom = findViewById(R.id.nextBtnBottom);
        this.prevBtnBottom = findViewById(R.id.prevBtnBottom);
        this.currentImageEditText = findViewWithId(R.id.imagePicker);
        this.totalImagesTextView = findViewWithId(R.id.imageQuantity);
        this.currentChapterEditText = findViewWithId(R.id.chapterPicker);
        this.totalChaptersTextView = findViewWithId(R.id.chapterQuantity);
        this.imageProgressBar = findViewWithId(R.id.imageProgressBar);
        this.imageOk = findViewWithId(R.id.imageOk);
        this.chapterOk = findViewWithId(R.id.chapterOk);
        this.drawerRightOffsetBottom = findViewById(R.id.drawer_right_offset_bottom);
        this.drawerRightOffsetTop = findViewById(R.id.drawer_right_offset_top);
        this.tutorialView = findViewById(R.id.tutorialView);
        settings = ApplicationSettings.get(this);
        nextBtn.setOnClickListener(this);
        prevBtn.setOnClickListener(this);
        nextBtnBottom.setOnClickListener(this);
        prevBtnBottom.setOnClickListener(this);
        imageOk.setOnClickListener(this);
        chapterOk.setOnClickListener(this);
        drawerRightOffsetTop.setOnTouchListener(new DisabledTouchEvent());
        drawerRightOffsetBottom.setOnTouchListener(new DisabledTouchEvent());
        toggleFullscreen(true);
        Button closeTutorial = findViewWithId(R.id.close_tutorial);
        closeTutorial.setOnClickListener(this);
        boolean isTutorialPassed = settings.isTutorialViewerPassed();
        if (isTutorialPassed) {
            this.tutorialView.setVisibility(View.GONE);
        }

        Intent intent = getIntent();
        if (savedInstanceState != null) {
            manga = savedInstanceState.getParcelable(Constants.MANGA_PARCEL_KEY);
            if (manga == null) {
                manga = intent.getParcelableExtra(Constants.MANGA_PARCEL_KEY);
            }
        } else {
            manga = intent.getParcelableExtra(Constants.MANGA_PARCEL_KEY);
        }
        TextView mangaTitleTextView = (TextView) findViewById(R.id.manga_title);
        mangaTitleTextView.setText(manga.getTitle());
        fromChapter = intent.getIntExtra(Constants.FROM_CHAPTER_KEY, -1);
        fromPage = intent.getIntExtra(Constants.FROM_PAGE_KEY, -1);

        //loading anims
        Animation nextInAnim = AnimationUtils.loadAnimation(getBaseContext(), R.anim.slide_in_right);
        Animation nextOutAnim = AnimationUtils.loadAnimation(getBaseContext(), R.anim.slide_out_left);
        Animation prevInAnim = AnimationUtils.loadAnimation(getBaseContext(), R.anim.slide_in_left);
        Animation prevOutAnim = AnimationUtils.loadAnimation(getBaseContext(), R.anim.slide_out_right);

        InAndOutAnim next = new InAndOutAnim(nextInAnim, nextOutAnim);
        next.setDuration(150);
        InAndOutAnim prev = new InAndOutAnim(prevInAnim, prevOutAnim);
        prev.setDuration(150);

        if (manga.isDownloaded()) {
            currentStrategy = new OfflineManga((LocalManga) manga, imageSwitcher, next, prev);
        } else {
            prepareOnlineManga();
            currentStrategy = new OnlineManga(manga, imageSwitcher, next, prev);
        }
        currentStrategy.setOnStrategyListener(this);
        currentStrategy.setObserver(this);
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            init();
        }
        closeKeyboard();
    }

    private void prepareOnlineManga() {

        //TODO: if got id don't go to DAO
        MangaDAO mangaDAO = ServiceContainer.getService(MangaDAO.class);

        Manga _manga = null;
        try {
            _manga = mangaDAO.getByLinkAndRepository(manga.getUri(), manga.getRepository(), manga.isDownloaded());
        } catch (DatabaseAccessException e) {
            e.printStackTrace();
            //TODO: show dialog "HISTORY WILL NOT BE SAVED" etc
        }
        if (_manga != null) {
            manga.setId(_manga.getId());
            manga.setFavorite(_manga.isFavorite());
        } else {
            try {
                long id = mangaDAO.addManga(manga);
                manga.setId((int) id);
            } catch (DatabaseAccessException e) {
                e.printStackTrace();
                //TODO: show dialog "HISTORY WILL NOT BE SAVED" etc
            }
        }

    }

    /**
     * Not standard method, because I want to handle
     * restorations in OnCreate
     * @param savedState
     */
    private void restoreState(final Bundle savedState) {
        final int currentChapterNumber = savedState.getInt(CURRENT_CHAPTER_KEY, 0);
        final int currentImageNumber = savedState.getInt(CURRENT_IMAGE_KEY, 0);
        ArrayList<MangaChapter> chapters = savedState.getParcelableArrayList(CHAPTERS_KEY);
        if (chapters != null) {
            manga.setChapters(chapters);
        }
        final ArrayList<String> uris = savedState.getStringArrayList(URIS_KEY);
        Log.d(TAG, "RESTORE CCN: " + currentChapterNumber + " CIN: " + currentImageNumber);
        currentStrategy.restoreState(uris, currentChapterNumber, currentImageNumber);


        progressDialog = Utils.easyDialogProgress(getSupportFragmentManager(), "Loading", "Initializing chapters");

        currentStrategy.initStrategy().then(new Promise.Action<MangaShowStrategy.Result, Promise<MangaShowStrategy.Result>>() {

            @Override
            public Promise<MangaShowStrategy.Result> action(final MangaShowStrategy.Result data, final boolean success) {

                try {
                    progressDialog.dismiss();
                    if (uris != null) {
                        try {
                            currentStrategy.showImage(currentImageNumber);
                        } catch (ShowMangaException e) {
                            Log.e(TAG, "Failed to show image: " + e.getMessage(), e);
                        }
                    } else {
                        OldPromise<MangaShowStrategy> promise = currentStrategy.showChapter(currentChapterNumber);
                        promise.after(new OldPromise.Action<MangaShowStrategy>() {

                            @Override
                            public void action(final MangaShowStrategy strategy, final boolean success) {
                                if (success) {
                                    try {
                                        currentStrategy.showImage(currentImageNumber);
                                    } catch (ShowMangaException e) {
                                        Log.e(TAG, "Failed to show image: " + e.getMessage(), e);
                                    }
                                }
                            }

                        });
                    }
                } catch (ShowMangaException e) {
                    Log.e(TAG, "Failed to show chapter: " + e.getMessage(), e);
                }
                return null;
            }

        });
    }

    private void init() {
        try {
            progressDialog = Utils.easyDialogProgress(getSupportFragmentManager(), "Loading", "Initializing chapters");
            currentStrategy.initStrategy().after(new OldPromise.Action<MangaShowStrategy>() {
                @Override
                public void action(final MangaShowStrategy strategy, final boolean success) {
                    progressDialog.dismiss();
                    if (manga.getChaptersQuantity() > 0) {
                        if (fromChapter == -1) {
                            fromChapter = manga.getChapters().get(0).getNumber();
                        }
                        try {
                            currentStrategy.showChapter(fromChapter).after(new OldPromise.Action<MangaShowStrategy>() {
                                @Override
                                public void action(final MangaShowStrategy strategy, final boolean success) {
                                    try {
                                        if (fromPage != -1) {
                                            strategy.showImage(fromPage);
                                        } else {
                                            strategy.showImage(0);
                                        }
                                    } catch (ShowMangaException e) {
                                        Log.e(TAG, e.getMessage(), e);
                                    }
                                }
                            });
                        } catch (ShowMangaException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }

                }
            });
        } catch (ShowMangaException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.prevBtn:
                onPrevious();
                break;
            case R.id.nextBtn:
                onNext();
                break;
            case R.id.prevBtnBottom:
                onPrevious();
                break;
            case R.id.nextBtnBottom:
                onNext();
                break;
            case R.id.imageOk:
                goToImageFromImagePicker();
                break;
            case R.id.chapterOk:
                goToChapterFromChapterPicker();
                break;
            case R.id.close_tutorial:
                this.tutorialView.setVisibility(View.GONE);
                settings.setTutorialViewerPassed(true);
                settings.update(this);
                break;
        }
    }

    private void goToChapterFromChapterPicker() {
        String chapterString = currentChapterEditText.getText().toString();
        Integer chapterNum = Integer.valueOf(chapterString) - 1;
        try {
            currentStrategy.showChapter(chapterNum).after(new OldPromise.Action<MangaShowStrategy>() {
                @Override
                public void action(final MangaShowStrategy strategy, final boolean success) {
                    try {
                        strategy.showImage(0);
                    } catch (ShowMangaException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            });
        } catch (ShowMangaException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        closeKeyboard();
    }

    private void goToImageFromImagePicker() {
        String imageString = currentImageEditText.getText().toString();
        Integer imageNum = Integer.valueOf(imageString) - 1;
        try {
            currentStrategy.showImage(imageNum);
        } catch (ShowMangaException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        closeKeyboard();
    }

    @Override
    public void onUpdate(final MangaShowStrategy strategy) {
        int currentChapter = strategy.getCurrentChapterNumber();
        String totalChapters = strategy.getTotalChaptersNumber();
        int currentImage = strategy.getCurrentImageNumber();
        int totalImages = strategy.getTotalImageNumber();
        currentChapterEditText.setText(String.valueOf(currentChapter + 1));
        currentImageEditText.setText(String.valueOf(currentImage + 1));
        totalImagesTextView.setText(String.valueOf(totalImages));
        totalChaptersTextView.setText(totalChapters);
    }

    private void onPrevious() {
        try {
            currentStrategy.previous();
        } catch (ShowMangaException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void onNext() {
        try {
            OldPromise<MangaShowStrategy> promise = currentStrategy.next();
            if (promise != null) {
                promise.after(new OldPromise.Action<MangaShowStrategy>() {
                    @Override
                    public void action(final MangaShowStrategy strategy, final boolean success) {
                        try {
                            strategy.showImage(0);
                        } catch (ShowMangaException e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
                });
            }
        } catch (ShowMangaException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        currentStrategy.destroy();
        int currentChapterNumber = currentStrategy.getCurrentChapterNumber();
        int currentImageNumber = currentStrategy.getCurrentImageNumber();
        Log.d(TAG, "CCN: " + currentChapterNumber + " CIN: " + currentImageNumber);
        outState.putInt(CURRENT_CHAPTER_KEY, currentStrategy.getCurrentChapterNumber());
        outState.putInt(CURRENT_IMAGE_KEY, currentStrategy.getCurrentImageNumber());
        outState.putParcelable(Constants.MANGA_PARCEL_KEY, manga);

        ArrayList<MangaChapter> chapterList = Utils.listToArrayList(manga.getChapters());
        if (chapterList != null) {
            outState.putParcelableArrayList(CHAPTERS_KEY, chapterList);
        }
        ArrayList<String> uris = Utils.listToArrayList(currentStrategy.getChapterUris());
        if (uris != null) {
            outState.putStringArrayList(URIS_KEY, uris);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        int currentChapterNumber = currentStrategy.getCurrentChapterNumber();
        int currentImageNumber = currentStrategy.getCurrentImageNumber();

        HistoryDAO historyDAO = ServiceContainer.getService(HistoryDAO.class);
        try {
            historyDAO.updateHistory(manga, currentChapterNumber, currentImageNumber);
        } catch (DatabaseAccessException e) {
            Log.e(TAG, "Failed to update history: " + e.getMessage());
        }
        finish();
    }

    // the part with MangaStrategyListener
    @Override
    public void onImageLoadStart(final MangaShowStrategy strategy) {
        imageProgressBar.setProgress(0);
        imageProgressBar.setMax(100);
        imageProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onImageLoadProgress(final MangaShowStrategy strategy, final int current, final int total) {
        imageProgressBar.setMax(total);
        imageProgressBar.setProgress(current);
    }

    @Override
    public void onImageLoadEnd(final MangaShowStrategy strategy, final boolean success, final String message) {
        imageProgressBar.setVisibility(View.GONE);
    }


    @Override
    public void onChapterInfoLoadStart(final MangaShowStrategy strategy) {
        progressDialog = Utils.easyDialogProgress(getSupportFragmentManager(), "Loading", "Loading chapter");
    }

    @Override
    public void onChapterInfoLoadEnd(final MangaShowStrategy strategy, final boolean success, final String message) {
        if (!success) {
            String errorMsg = Utils.errorMessage(this, message, R.string.p_failed_to_load_chapter_info);
            Utils.showToast(this, errorMsg);
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    // MangaStrategyListener realization end

    private class SubsamplingImageViewFactory implements ViewSwitcher.ViewFactory {

        @Override
        public View makeView() {
            SubsamplingScaleImageView touchImageView = new SubsamplingScaleImageView(MangaViewerActivity.this);

            touchImageView.setLayoutParams(new
                    ImageSwitcher.LayoutParams(
                    ImageSwitcher.LayoutParams.MATCH_PARENT, ImageSwitcher.LayoutParams.MATCH_PARENT));
            touchImageView.setVisibility(View.INVISIBLE);
            touchImageView.setMaxScale(4);
            //touchImageView.setDebug(true);
            return touchImageView;
        }

    }

    private void closeKeyboard() {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manga_viewer_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.full_screen:
                toggleFullscreen(true);
                return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    private void toggleFullscreen(final boolean fullscreen) {
        this.isFullscreen = fullscreen;
        boolean oldFullscreen = settings.isViewerFullscreen();
        if (oldFullscreen != fullscreen) {
            settings.setViewerFullscreen(fullscreen);
            settings.update(this);
        }
        if (fullscreen) {
            getSupportActionBar().hide();
        } else {
            getSupportActionBar().show();
        }
    }

    private class DisabledTouchEvent implements View.OnTouchListener {

        @Override
        public boolean onTouch(final View view, final MotionEvent motionEvent) {
            motionEvent.setEdgeFlags(4099);
            return false;
        }

    }

}