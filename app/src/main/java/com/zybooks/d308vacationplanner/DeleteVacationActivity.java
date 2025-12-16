package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

public class DeleteVacationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        long vacationId = -1L;
        if (intent != null) {
            vacationId = intent.getLongExtra(EditVacationActivity.EXTRA_VACATION_ID, -1L);
        }

        if (vacationId == -1L) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        VacationRepository repo = VacationRepository.getInstance(this);
        boolean hasExcursions = false;
        // Check excursions (try typed method then fallback)
        try {
            Method excMethod = findMethod(repo, "getExcursionsForVacation", long.class, Long.class);
            if (excMethod != null) {
                Object arg = boxIfNeeded(excMethod.getParameterTypes()[0], vacationId);
                Object res = excMethod.invoke(repo, arg);
                if (res instanceof List) {
                    hasExcursions = !((List<?>) res).isEmpty();
                }
            } else {
                Method getAll = findMethod(repo, "getExcursions");
                if (getAll != null) {
                    Object allRes = getAll.invoke(repo);
                    if (allRes instanceof List) {
                        List<?> excursions = (List<?>) allRes;
                        for (Object exc : excursions) {
                            if (exc == null) continue;
                            Long vid = null;
                            String[] candidateMethods = {"getVacationId", "getVacation", "getVacationID", "getVacId"};
                            for (String name : candidateMethods) {
                                try {
                                    Method gv = exc.getClass().getMethod(name);
                                    Object val = gv.invoke(exc);
                                    if (val != null) {
                                        try {
                                            vid = Long.valueOf(String.valueOf(val));
                                        } catch (NumberFormatException ignored) { }
                                        break;
                                    }
                                } catch (Exception ignored) { }
                            }
                            if (vid != null && vid.longValue() == vacationId) {
                                hasExcursions = true;
                                break;
                            }
                        }
                    }
                } else {
                    hasExcursions = true;
                }
            }
        } catch (Exception e) {
            hasExcursions = true;
        }

        if (hasExcursions) {
            Toast.makeText(this, "Cannot delete vacation with excursions.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Obtain the Vacations entity instance from repository (if available)
        Vacations vacObj = getVacationObjectById(repo, vacationId);

        // Try delete via repository methods (prioritize object-param deletes first)
        boolean deleted = tryDeleteViaMethods(repo, vacationId, vacObj);

        // Fallback: remove from the repository list (only if underlying implementation exposes a mutable list)
        if (!deleted) {
            deleted = removeFromVacationsList(repo, vacationId);
        }

        Intent result = new Intent();
        result.putExtra(EditVacationActivity.EXTRA_VACATION_ID, vacationId);
        if (deleted) {
            Toast.makeText(this, "Vacation Deleted", Toast.LENGTH_SHORT).show();
            result.putExtra(EditVacationActivity.EXTRA_VACATION_DELETE, true);
            // return standard OK so caller can refresh UI
            setResult(RESULT_OK, result);
        } else {
            Toast.makeText(this, "Vacation Failed", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    // Try object-param methods first, then id-based methods.
    private boolean tryDeleteViaMethods(Object repo, long id, Vacations vacObj) {
        String[] candidateNames = {
                "deleteVacation", "removeVacation",
                "delete", "remove",
                "deleteById", "removeById",
                "deleteVacationById", "removeVacationById",
                "deleteVacations", "deleteEntity"
        };

        // Try methods that accept the Vacations object first (these should remove the DB row when using Room DAO)
        for (String name : candidateNames) {
            try {
                Method mObj = repo.getClass().getMethod(name, Vacations.class);
                if (vacObj != null) {
                    Object res = mObj.invoke(repo, vacObj);
                    if (isSuccessfulResult(res)) return true;
                }
            } catch (NoSuchMethodException ignored) { }
            catch (Exception ignored) { }
        }

        // Next try id-based methods (primitive long or Long)
        Class<?>[] idParamTypes = new Class<?>[]{long.class, Long.class};
        for (String name : candidateNames) {
            for (Class<?> pType : idParamTypes) {
                try {
                    Method m = repo.getClass().getMethod(name, pType);
                    Object arg = boxIfNeeded(pType, id);
                    Object res = m.invoke(repo, arg);
                    if (isSuccessfulResult(res)) return true;
                } catch (NoSuchMethodException ignored) { }
                catch (Exception ignored) { }
            }
        }

        return false;
    }

    private boolean isSuccessfulResult(Object res) {
        if (res == null) return true; // void -> assumed success
        if (res instanceof Boolean) return (Boolean) res;
        if (res instanceof Number) return ((Number) res).intValue() > 0;
        return false;
    }

    private Vacations getVacationObjectById(Object repo, long id) {
        try {
            Method getVacations = findMethod(repo, "getVacations");
            if (getVacations != null) {
                Object all = getVacations.invoke(repo);
                if (all instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) all;
                    for (Object o : list) {
                        if (o == null) continue;
                        try {
                            Method getId = o.getClass().getMethod("getId");
                            Object idVal = getId.invoke(o);
                            if (idVal != null && Long.parseLong(String.valueOf(idVal)) == id) {
                                if (o instanceof Vacations) return (Vacations) o;
                                // not castable -> skip object-param deletes
                                return null;
                            }
                        } catch (Exception ignored) { }
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private boolean removeFromVacationsList(Object repo, long id) {
        try {
            Method getVacations = findMethod(repo, "getVacations");
            if (getVacations != null) {
                Object all = getVacations.invoke(repo);
                if (all instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) all;
                    Iterator<Object> it = list.iterator();
                    while (it.hasNext()) {
                        Object v = it.next();
                        if (v == null) continue;
                        try {
                            Method getId = v.getClass().getMethod("getId");
                            Object idVal = getId.invoke(v);
                            if (idVal != null && Long.parseLong(String.valueOf(idVal)) == id) {
                                it.remove();
                                return true;
                            }
                        } catch (Exception ignored) { }
                    }
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private Method findMethod(Object target, String name, Class<?>... extraTypes) {
        try {
            if (extraTypes != null && extraTypes.length > 0) {
                for (Class<?> t : extraTypes) {
                    try {
                        return target.getClass().getMethod(name, t);
                    } catch (NoSuchMethodException ignored) { }
                }
            }
            return target.getClass().getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Object boxIfNeeded(Class<?> paramType, long id) {
        if (paramType == Long.class) return Long.valueOf(id);
        if (paramType == long.class) return id;
        return id;
    }
}
