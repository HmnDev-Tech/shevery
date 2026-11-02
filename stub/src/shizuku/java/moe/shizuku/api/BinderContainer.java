package moe.shizuku.api;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Minimal copy of the API {@code BinderContainer} for the Shizuku Stub process.
 * <p>
 * The stub forwards a {@code Bundle} received from a client to the main Shevery
 * provider. Without this class in the stub classpath, unparceling the Bundle
 * throws {@code BadParcelableException} and the forwarded binder is lost.
 */
public class BinderContainer implements Parcelable {

    public IBinder binder;

    public BinderContainer(IBinder binder) {
        this.binder = binder;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(this.binder);
    }

    protected BinderContainer(Parcel in) {
        this.binder = in.readStrongBinder();
    }

    public static final Creator<BinderContainer> CREATOR = new Creator<BinderContainer>() {
        @Override
        public BinderContainer createFromParcel(Parcel source) {
            return new BinderContainer(source);
        }

        @Override
        public BinderContainer[] newArray(int size) {
            return new BinderContainer[size];
        }
    };
}
