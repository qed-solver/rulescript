package rulescript_test

import (
	"testing"
)

func TestSimplifySameVarInequalities(t *testing.T) {
	testSimplifySameVar(t, "SimplifySameVarInequalities", true)
}

func TestSQLSimplifySameVarInequalities(t *testing.T) {
	expectSQLFires(t, "SimplifySameVarInequalities", "SELECT x FROM u WHERE x <> x")
}
