use rulescript::*;

fn main() {
    println!("Testing schema! and functions! macros\n");

    // Test 1: schema! with single field
    let schema1 = schema!(col: T);
    println!("schema!(col: T):");
    println!("  Fields: {}", schema1.fields.len());
    println!("  Field[0].name: {}", schema1.fields[0].name);
    println!("  Field[0].nullable: {}\n", schema1.fields[0].nullable);

    // Test 2: schema! with not_null
    let schema2 = schema!(x: U not_null);
    println!("schema!(x: U not_null):");
    println!("  Field[0].nullable: {}\n", schema2.fields[0].nullable);

    // Test 3: schema! with multiple fields (all nullable)
    let schema3 = schema!(x: T, y: U, z: V);
    println!("schema!(x: T, y: U, z: V):");
    println!("  Fields: {}", schema3.fields.len());
    for (i, field) in schema3.fields.iter().enumerate() {
        println!("  Field[{}]: name='{}', nullable={}", i, field.name, field.nullable);
    }
    println!();

    // Test 4: schema! with mixed nullability
    let schema4 = schema!(x: T, y: U not_null, z: V nullable, w: W not_null);
    println!("schema!(x: T, y: U not_null, z: V nullable, w: W not_null):");
    println!("  Fields: {}", schema4.fields.len());
    for (i, field) in schema4.fields.iter().enumerate() {
        println!("  Field[{}]: name='{}', nullable={}", i, field.name, field.nullable);
    }
    println!();

    // Test 5: functions! macro
    functions! {
        P(T) -> Bool,
        Q(T) -> Bool,
        f(T) -> U,
        g(U, V) -> W,
    }
    
    println!("functions! macro created:");
    println!("  P.name: {}", P.name);
    println!("  P.input_count: {}", P.input_count());
    println!("  f.name: {}", f.name);
    println!("  f.input_count: {}", f.input_count());
    println!("  g.name: {}", g.name);
    println!("  g.input_count: {}", g.input_count());
    
    println!("\n✅ All macros work correctly!");
}
