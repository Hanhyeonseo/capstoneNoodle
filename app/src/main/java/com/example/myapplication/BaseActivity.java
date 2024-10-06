package com.example.myapplication;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

//액티비티 전환 시 애니메이션 효과를 구현하기 위한 베이스 액티비티

public abstract class BaseActivity extends AppCompatActivity {

    private final TransitionMode transitionMode;
    private boolean isReverse = false;

    protected BaseActivity(TransitionMode transitionMode) {
        this.transitionMode = transitionMode;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTransition();
    }

    @Override
    public void finish() {
        super.finish();
        isReverse = true;
        applyTransition();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (isFinishing()) {
            isReverse = true;
            applyTransition();
        }
    }

    private void applyTransition() {
        switch (transitionMode) {
            case HORIZON:
                if (isReverse) {
                    overridePendingTransition(R.anim.horizon_enter_reverse, R.anim.horizon_exit_reverse);
                } else {
                    overridePendingTransition(R.anim.horizon_enter, R.anim.horizon_exit);
                }
                break;
            case VERTICAL:
                if (isReverse) {
                    overridePendingTransition(R.anim.vertical_enter_reverse, R.anim.vertical_exit_reverse);
                } else {
                    overridePendingTransition(R.anim.vertical_enter, R.anim.vertical_exit);
                }
                break;
            case NONE:
            default:
                overridePendingTransition(0, 0);
                break;
        }
        isReverse = false;
    }

    public enum TransitionMode {
        NONE,
        HORIZON,
        VERTICAL
    }
}
