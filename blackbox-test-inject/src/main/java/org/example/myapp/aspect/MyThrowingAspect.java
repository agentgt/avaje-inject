package org.example.myapp.aspect;

import io.avaje.inject.AspectProvider;
import io.avaje.inject.Invocation;
import io.avaje.inject.MethodInterceptor;
import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class MyThrowingAspect implements AspectProvider<MyThrowing>, MethodInterceptor {

  private List<String> trace = new ArrayList<>();

  @Override
  public MethodInterceptor interceptor(Method method, MyThrowing around) {
    return this;
  }

  @Override
  public void invoke(Invocation invoke) throws Throwable {
    throw new ArithmeticException("my interceptor throws this");
  }
}
