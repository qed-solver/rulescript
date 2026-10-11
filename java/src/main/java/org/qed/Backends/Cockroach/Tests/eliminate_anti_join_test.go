package rulescript_test

import (
	"testing"
)

func TestEliminateAntiJoin(t *testing.T) {
	testAntiJoinEmpty(t, "EliminateAntiJoin")
}
