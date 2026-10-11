package rulescript_test

import (
	"testing"
)

func TestSimplifySameVarEqualities(t *testing.T) {
	testSimplifySameVar(t, "SimplifySameVarEqualities", false)
}

func TestSQLSimplifySameVarEqualities(t *testing.T) {
	expectSQLFires(t, "SimplifySameVarEqualities", "SELECT x FROM u WHERE x = x")
}
