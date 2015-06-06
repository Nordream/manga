package com.danilov.mangareaderplus.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.httpimage.HttpImageManager;
import com.danilov.mangareaderplus.R;
import com.danilov.mangareaderplus.activity.MangaInfoActivity;
import com.danilov.mangareaderplus.core.adapter.BaseAdapter;
import com.danilov.mangareaderplus.core.database.DatabaseAccessException;
import com.danilov.mangareaderplus.core.database.HistoryDAO;
import com.danilov.mangareaderplus.core.database.MangaDAO;
import com.danilov.mangareaderplus.core.model.LocalManga;
import com.danilov.mangareaderplus.core.model.Manga;
import com.danilov.mangareaderplus.core.service.LocalImageManager;
import com.danilov.mangareaderplus.core.util.Constants;
import com.danilov.mangareaderplus.core.util.ServiceContainer;
import com.danilov.mangareaderplus.core.util.Utils;

import java.util.List;

/**
 * Created by Semyon on 22.12.2014.
 */
public class FavoritesFragment extends BaseFragment implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    private static final String TAG = "FavoritesFragment";

    private boolean isInMultiChoice = false;

    private View view;
    private ProgressBar downloadedProgressBar;

    private LocalImageManager localImageManager = null;
    private HttpImageManager httpImageManager = null;
    private MangaDAO mangaDAO = null;
    private HistoryDAO historyDAO = null;

    private int sizeOfImage;

    private FavoritesAdapter adapter = null;
    private GridView gridView = null;

    public static FavoritesFragment newInstance() {
        return new FavoritesFragment();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.manga_downloaded_fragment, container, false);
        return view;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        sizeOfImage = getActivity().getResources().getDimensionPixelSize(R.dimen.grid_item_height);
        localImageManager = ServiceContainer.getService(LocalImageManager.class);
        httpImageManager = ServiceContainer.getService(HttpImageManager.class);
        mangaDAO = ServiceContainer.getService(MangaDAO.class);
        historyDAO = ServiceContainer.getService(HistoryDAO.class);
        gridView = (GridView) view.findViewById(R.id.grid_view);
        downloadedProgressBar = (ProgressBar) view.findViewById(R.id.downloaded_progress_bar);
        gridView.setOnItemClickListener(this);
        gridView.setOnItemLongClickListener(this);
        loadDownloadedManga();
        super.onActivityCreated(savedInstanceState);
    }

    private void loadDownloadedManga() {
        downloadedProgressBar.setVisibility(View.VISIBLE);
        final Context context = getActivity();
        Thread thread = new Thread() {
            @Override
            public void run() {
                boolean _success = true;
                String _error = null;
                try {
                    List<Manga> mangas = mangaDAO.getFavorite();
                    adapter = new FavoritesAdapter(context, 0, mangas);
                } catch (DatabaseAccessException e) {
                    _success = false;
                    _error = e.getMessage();
                    Log.e(TAG, "Failed to get favorite manga: " + _error);
                }
                final boolean success = _success;
                final String error = _error;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        downloadedProgressBar.setVisibility(View.INVISIBLE);
                        if (success) {
                            gridView.setAdapter(adapter);
                        } else {
                            String formedError = Utils.stringResource(getActivity(), R.string.p_failed_to_show_loaded);
                            Utils.showToast(getActivity(), formedError + error);
                        }
                    }
                });
            }

        };
        thread.start();
    }


    @Override
    public void onItemClick(final AdapterView<?> adapterView, final View view, final int i, final long l) {
        Manga manga = adapter.getItem(i);

        Intent intent = new Intent(getActivity().getApplicationContext(), MangaInfoActivity.class);


        ImageView iv = (ImageView) view.findViewById(R.id.manga_cover);
        int[] onScreenLocation = new int[2];
        iv.getLocationOnScreen(onScreenLocation);

        intent.putExtra(MangaInfoActivity.EXTRA_LEFT, onScreenLocation[0]);
        intent.putExtra(MangaInfoActivity.EXTRA_TOP, onScreenLocation[1]);
        intent.putExtra(MangaInfoActivity.EXTRA_WIDTH, iv.getWidth());
        intent.putExtra(MangaInfoActivity.EXTRA_HEIGHT, iv.getHeight());
        intent.putExtra(MangaInfoActivity.EXTRA_HEIGHT, iv.getHeight());

        intent.putExtra(Constants.MANGA_PARCEL_KEY, manga);
        startActivity(intent);

        getActivity().overridePendingTransition(0, 0);
    }

    @Override
    public boolean onItemLongClick(final AdapterView<?> adapterView, final View view, final int i, final long l) {
        return false;
    }

    private class FavoritesAdapter extends BaseAdapter<Holder, Manga> {

        private List<Manga> mangas = null;

        public FavoritesAdapter(final Context context, final int resource, final List<Manga> objects) {
            super(context, resource, objects);
            mangas = objects;
        }

        @Override
        public void onBindViewHolder(final Holder holder, final int position) {
            Manga manga = mangas.get(position);
            holder.title.setText(manga.getTitle());

            if (manga.isDownloaded()) {
                holder.isOnline.setVisibility(View.INVISIBLE);
                LocalManga localManga = (LocalManga) manga;
                String mangaUri = localManga.getLocalUri();
                Bitmap bitmap = localImageManager.loadBitmap(holder.mangaCover, mangaUri + "/cover", sizeOfImage);
                if (bitmap != null) {
                    holder.mangaCover.setImageBitmap(bitmap);
                }
            } else {
                holder.isOnline.setVisibility(View.VISIBLE);
                if (manga.getCoverUri() != null) {
                    //TODO: временный хак! Потом заблочить добавление в избранное если нет картинки (или придумать что-то ещё)
                    Uri coverUri = Uri.parse(manga.getCoverUri());
                    HttpImageManager.LoadRequest request = HttpImageManager.LoadRequest.obtain(coverUri, holder.mangaCover, sizeOfImage);
                    Bitmap bitmap = httpImageManager.loadImage(request);
                    if (bitmap != null) {
                        holder.mangaCover.setImageBitmap(bitmap);
                    }
                }
            }

        }

        @Override
        public int getCount() {
            if (mangas == null) {
                return 0;
            }
            return mangas.size();
        }

        @Override
        public Holder onCreateViewHolder(final ViewGroup viewGroup, final int position) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.favorites_grid_item, viewGroup, false);
            return new Holder(v);
        }

    }

    private static class Holder extends BaseAdapter.BaseHolder {

        public TextView title;
        public View isOnline;
        public ImageView mangaCover;

        protected Holder(final View view) {
            super(view);
            mangaCover = findViewById(R.id.manga_cover);
            isOnline = findViewById(R.id.is_online);
            title = findViewById(R.id.manga_title);
        }

    }

}
