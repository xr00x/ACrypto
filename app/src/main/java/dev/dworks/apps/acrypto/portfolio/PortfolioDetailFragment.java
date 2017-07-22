/*
 * Copyright (C) 2014 Hari Krishna Dulipudi
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.dworks.apps.acrypto.portfolio;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

import dev.dworks.apps.acrypto.R;
import dev.dworks.apps.acrypto.common.DialogFragment;
import dev.dworks.apps.acrypto.entity.Portfolio;
import dev.dworks.apps.acrypto.misc.AnalyticsManager;
import dev.dworks.apps.acrypto.misc.FirebaseHelper;
import dev.dworks.apps.acrypto.settings.SettingsActivity;
import dev.dworks.apps.acrypto.utils.Utils;
import dev.dworks.apps.acrypto.view.Spinner;

import static dev.dworks.apps.acrypto.settings.SettingsActivity.CURRENCY_FROM_DEFAULT;
import static dev.dworks.apps.acrypto.settings.SettingsActivity.CURRENCY_TO_DEFAULT;
import static dev.dworks.apps.acrypto.utils.Utils.BUNDLE_PORTFOLIO;
import static dev.dworks.apps.acrypto.utils.Utils.REQUIRED;

/**
 * Dialog to create a new portfolio.
 */
public class PortfolioDetailFragment extends DialogFragment {
    private static final String TAG = "PortfolioDetail";
    private AppCompatEditText mName;
    private Portfolio mPortfolio;
    private Spinner mCurrencyToSpinner;
    private String curencyTo;
    private String name;
    private String description;
    private EditText mDescription;

    public static void show(FragmentManager fm, Portfolio portfolio) {
        final PortfolioDetailFragment dialog = new PortfolioDetailFragment();
        final Bundle args = new Bundle();
        args.putSerializable(BUNDLE_PORTFOLIO, portfolio);
        dialog.setArguments(args);
        dialog.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPortfolio = (Portfolio)getArguments().getSerializable(BUNDLE_PORTFOLIO);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Context context = getActivity();

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final LayoutInflater dialogInflater = getActivity().getLayoutInflater();

        final View view = dialogInflater.inflate(R.layout.dialog_portfolio_detail, null, false);
        mName = (AppCompatEditText) view.findViewById(R.id.name);
        mDescription = (EditText) view.findViewById(R.id.description);
        view.findViewById(R.id.info).setVisibility(Utils.getVisibility(isNew()));
        mCurrencyToSpinner = (Spinner) view.findViewById(R.id.currencyToSpinner);
        mCurrencyToSpinner.getPopupWindow().setWidth(300);
        mCurrencyToSpinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener<String>() {

            @Override
            public void onItemSelected(Spinner view, int position, long id, String item) {
                curencyTo = item;
            }
        });

        if(!isNew()){
            name = mPortfolio.name;
            description = mPortfolio.description;
            curencyTo = mPortfolio.currency;
            mName.setText(name);
            mDescription.setText(description);
            mCurrencyToSpinner.setEnabled(false);
        }
        fetchCurrencyToData();
        builder.setTitle( (isNew() ? "New" : "Edit") + " Portfolio");
        builder.setView(view);

        builder.setPositiveButton(isNew() ? "ADD" : "UPDATE", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                save();
            }
        });
        if(!isNew()) {
            builder.setNeutralButton("DELETE", new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    delete();
                }
            });
        }
        builder.setNegativeButton(android.R.string.cancel, null);

        return builder.create();
    }

    private void delete() {
        if(!Utils.isNetConnected(getActivity())){
            Utils.showNoInternetSnackBar(getActivity(), null);
            return;
        }

        DatabaseReference reference = FirebaseHelper.getFirebaseDatabaseReference()
                .child("portfolios")
                .child(FirebaseHelper.getCurrentUid())
                .child(mPortfolio.id);
        reference.removeValue(new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if(null == databaseError && null != getActivity()){
                    AnalyticsManager.logEvent("portfolio_deleted");
                    dismiss();
                }
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(!getShowsDialog()){
            return;
        }
        Button button = getButton(getDialog(), DialogInterface.BUTTON_POSITIVE);
        if(null != button)
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
    }

    private boolean isNew() {
        return null == mPortfolio;
    }

    private void fetchCurrencyToData() {
        FirebaseHelper.getFirebaseDatabaseReference("master/currency")
                .orderByChild("order")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        ArrayList<String> coins = new ArrayList<>();
                        for (DataSnapshot childSnapshot : dataSnapshot.getChildren()){
                            String coin = childSnapshot.getKey();
                            coins.add(coin);
                        }
                        coins.add(CURRENCY_FROM_DEFAULT);
                        mCurrencyToSpinner.setItems(coins);
                        Utils.setSpinnerValue(mCurrencyToSpinner, CURRENCY_TO_DEFAULT, getCurrentCurrencyTo());
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
    }

    public String getCurrentCurrencyTo(){
        return TextUtils.isEmpty(curencyTo) ? SettingsActivity.getCurrencyTo() : curencyTo;
    }

    private void save() {
        if (TextUtils.isEmpty(mName.getText())) {
            mName.setError(REQUIRED);
            return;
        }

        if(!Utils.isNetConnected(getActivity())){
            Utils.showNoInternetSnackBar(getActivity(), null);
            return;
        }

        Portfolio portfolio = new Portfolio(mName.getText().toString(),
                getCurrentCurrencyTo(), mDescription.getText().toString());
        DatabaseReference reference = FirebaseHelper.getFirebaseDatabaseReference()
                .child("portfolios")
                .child(FirebaseHelper.getCurrentUid());

        DatabaseReference databaseReference;
        if(isNew()){
            databaseReference = reference.push();
            portfolio.id = databaseReference.getKey();
        } else {
            databaseReference = reference.child(mPortfolio.id);
            portfolio.id = mPortfolio.id;
        }
        databaseReference.setValue(portfolio, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if(null == databaseError && null != getActivity()){
                    AnalyticsManager.logEvent(isNew() ? "portfolio_added" : "portfolio_edited");
                }
            }
        });
    }
}
