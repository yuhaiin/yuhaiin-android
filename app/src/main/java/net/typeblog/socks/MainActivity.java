package net.typeblog.socks;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProfileFragment fragment = new ProfileFragment();
        fragment.setContext(getApplicationContext());
        this.getSupportFragmentManager().
                beginTransaction().
                replace(android.R.id.content,fragment).commit();
    }
}
