package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullAndOr(t *testing.T) {
	rule := loadRule(t, "FoldNullAndOr")
	e := newEnv(t)
	s, _ := e.scan("u", 1)
	nullBool := e.f.ConstructNull(types.Bool)
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(nullBool, nullBool))), e.f.ConstructSelect(s, e.filters(nullBool)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(nullBool, nullBool))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(nullBool, e.f.ConstructTrue()))))
}

func TestSQLFoldNullAndOr(t *testing.T) {
	expectSQLFires(t, "FoldNullAndOr", "SELECT x FROM u WHERE NULL AND NULL")
}
