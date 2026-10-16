package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryRemapSelectOuterCols(t *testing.T) {
	rule := loadRule(t, "TryRemapSelectOuterCols")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	positive := func(c opt.ColumnID) memo.RelExpr {
		return e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(c), e.num(0))))
	}
	equal := e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols[1])))
	e.expectRewrite(rule, e.f.ConstructSelect(positive(cols[0]), equal), e.f.ConstructSelect(positive(cols[1]), equal))
	e.expectNoMatch(rule, e.f.ConstructSelect(positive(cols[0]),
		e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols[2])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, equal))
}
