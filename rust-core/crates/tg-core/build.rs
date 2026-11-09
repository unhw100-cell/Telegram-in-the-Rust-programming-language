fn main() {
    uniffi::generate_scaffolding("src/lib.rs").expect("Failed to build UniFFI scaffolding");
}
