/// Comprehensive Rust parser test input for regression testing.
/// Includes use, struct, enum, trait, impl, functions, and comments.

use std::collections::HashMap;
use std::io::{self, Write};

// Single-line comment

/* Single-line block comment */

/* Multi-line block comment without per-line asterisks */

/// Abstract base-like trait.
pub trait AbstractBase {
    fn get_base_value(&self) -> i32;
    fn abstract_hook(&self) -> i32;
}

/// Marker-style interface.
pub trait MarkerInterface {
    fn mark(&self);
    fn default_mark(&self) {
        self.mark();
    }
}

/// Nested-style interface extending MarkerInterface.
pub trait NestedInterface: MarkerInterface {
    fn abstract_interface_method(&self);
}

/// Nested enum with variants and a method.
#[derive(Clone, Copy, PartialEq, Eq)]
pub enum NestedEnum {
    Alpha,
    Beta,
    Gamma,
}

impl NestedEnum {
    pub fn is_alpha(self) -> bool {
        match self {
            NestedEnum::Alpha => true,
            _ => false,
        }
    }
}

pub enum TopLevelEnum {
    One,
    Two,
    Three,
}

/// Main regression type.
pub struct ParserTestInput {
    /// Stored base value.
    base_value: i32,
    protected_transient_field: Option<String>,
    nested_generic_field: HashMap<String, Vec<i32>>,
    final_array_field: [f64; 2],
}

pub const PUBLIC_STATIC_FINAL_INT: i32 = 42;

static mut PRIVATE_STATIC_VOLATILE_FLAG: bool = false;

impl ParserTestInput {
    pub fn new() -> Self {
        ParserTestInput {
            base_value: 0,
            protected_transient_field: None,
            nested_generic_field: HashMap::new(),
            final_array_field: [1.0, 2.0],
        }
    }

    pub fn with_name(name: String, values: &[i32]) -> Self {
        ParserTestInput {
            base_value: values.len() as i32,
            protected_transient_field: Some(name),
            nested_generic_field: HashMap::new(),
            final_array_field: [1.0, 2.0],
        }
    }

    pub fn run(&self) {
        assert!(PUBLIC_STATIC_FINAL_INT > 0);
    }

    pub fn call(&mut self) -> String {
        let _ = self.do_work(true, 1, 2, 'x', 3, 4, 5.0, 6.0, None);
        String::from("ok")
    }

    pub fn generic_method<T>(&self, value: T) -> T {
        value
    }

    pub fn do_work(
        &mut self,
        flag: bool,
        mut b: u8,
        mut s: i16,
        c: char,
        i: i32,
        l: i64,
        f: f32,
        d: f64,
        obj: Option<&str>,
    ) -> bool {
        if flag {
            return true;
        }
        if obj.is_some() {
            return false;
        }
        match i {
            0 => {}
            1 => {
                for n in 0..3 {
                    if n == 1 {
                        continue;
                    }
                    if n == 2 {
                        break;
                    }
                }
            }
            _ => {
                while s > 0 {
                    s -= 1;
                }
                loop {
                    b = b.wrapping_add(1);
                    if b >= 3 {
                        break;
                    }
                }
            }
        }
        if l < 0 {
            let _ = io::Error::new(io::ErrorKind::Other, "negative");
        }
        unsafe {
            PRIVATE_STATIC_VOLATILE_FLAG = i.abs() > 0;
        }
        self.nested_generic_field
            .insert(String::from("key"), Vec::new());
        f > 0.0 && d > 0.0 && c != '\0'
    }
}

impl AbstractBase for ParserTestInput {
    fn get_base_value(&self) -> i32 {
        self.base_value
    }

    fn abstract_hook(&self) -> i32 {
        self.get_base_value()
    }
}

impl MarkerInterface for ParserTestInput {
    fn mark(&self) {}
}

pub struct StaticNested {
    nested_field: i32,
}

impl StaticNested {
    pub fn new(nested_field: i32) -> Self {
        StaticNested { nested_field }
    }

    pub fn get_nested_field(&self) -> i32 {
        self.nested_field
    }
}

pub fn package_private_static_method() {}

pub fn module_helper(path: &str, out: &mut dyn Write) -> io::Result<String> {
    out.write_all(path.as_bytes())?;
    Ok(path.to_string())
}
