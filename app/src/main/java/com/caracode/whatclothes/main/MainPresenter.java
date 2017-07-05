package com.caracode.whatclothes.main;

import android.support.annotation.NonNull;

import com.caracode.whatclothes.api.response.FiveDayResponse;
import com.caracode.whatclothes.service.PhotoService;
import com.caracode.whatclothes.service.WeatherService;

import net.grandcentrix.thirtyinch.TiPresenter;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

class MainPresenter extends TiPresenter<MainView> {

    private static final double MIN_TEMP = -100;
    private static final double MAX_TEMP = 100;
    private static final String FLICKER_PHOTO_URL_FORMAT = "https://farm%1$s.staticflickr.com/%2$s/%3$s_%4$s.jpg";

    private final WeatherService weatherService;
    private final PhotoService photoService;
    private final CompositeDisposable networkDisposable;
    private final CompositeDisposable viewDisposable;

    MainPresenter(@NonNull WeatherService weatherService, @NonNull PhotoService photoService,
                  @NonNull CompositeDisposable networkDisposable, @NonNull CompositeDisposable viewDisposable) {
        this.weatherService = weatherService;
        this.photoService = photoService;
        this.networkDisposable = networkDisposable;
        this.viewDisposable = viewDisposable;
    }

    @Override
    protected void onAttachView(@NonNull final MainView view) {
        super.onAttachView(view);

        Single<FiveDayResponse> weather = weatherService.getWeather();
        Single<String> displayableDate =
                weather.map(fiveDayResponse ->
                                fiveDayResponse.threeHourlyUpdates().get(0).dateTime().toString("EEE d MMM"));

        Observable<FiveDayResponse.ThreeHourlyUpdate> threeHourlyUpdateObservable =
                weather.toObservable()
                        .flatMapIterable(FiveDayResponse::threeHourlyUpdates);

        Single<Double> minTemperature =
                threeHourlyUpdateObservable
                        .reduce(MAX_TEMP, (running, thisUpdate) ->
                                Math.min(running, thisUpdate.main().minTemp()));
        Single<Double> maxTemperature =
                threeHourlyUpdateObservable
                        .reduce(MIN_TEMP, (running, thisUpdate) ->
                                Math.max(running, thisUpdate.main().maxTemp()));

        networkDisposable.add(
                Single.zip(displayableDate, minTemperature, maxTemperature, MainViewModel::create)
                .subscribe(
                        this::showWeather,
                        Throwable::printStackTrace));

        networkDisposable.add(
                photoService
                        .getPhotos()
                        .map(photosResponse -> photosResponse.photos().photos().get(3))
                        .map(photo -> String.format(FLICKER_PHOTO_URL_FORMAT,
                                                photo.farm(), photo.server(), photo.id(), photo.secret()))
                        .subscribe(
                                this::showPhoto,
                                Throwable::printStackTrace));


//        viewDisposable.add(
//                view.onButtonPress().subscribe(
//                        o -> view.showWeather("Changed date")));
    }

    @Override
    protected void onDetachView() {
        super.onDetachView();
        if (!viewDisposable.isDisposed()) {
            viewDisposable.dispose();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!networkDisposable.isDisposed()) {
            networkDisposable.dispose();
        }
    }

    private void showWeather(MainViewModel mainViewModel) {
        if (isViewAttached() && getView() != null) {
            getView().showWeather(mainViewModel);
        }
    }

    private void showPhoto(String photoUrl) {
        if (isViewAttached() && getView() != null) {
            getView().showPhoto(photoUrl);
        }
    }
}
