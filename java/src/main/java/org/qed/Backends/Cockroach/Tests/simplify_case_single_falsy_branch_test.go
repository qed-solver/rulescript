package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifyCaseSingleFalsyBranch(t *testing.T) {
	rule := loadRule(t, "SimplifyCaseSingleFalsyBranch")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	cond := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	null := e.f.ConstructNull(types.Bool)
	caseOf := func(when opt.ScalarExpr) opt.ScalarExpr {
		return e.f.ConstructOr(e.f.ConstructAnd(cond, when), e.f.ConstructAnd(e.f.ConstructNot(cond), null))
	}
	before := e.f.ConstructSelect(s, e.filters(caseOf(e.f.ConstructFalse())))
	e.expectRewrite(rule, before, e.f.ConstructSelect(s, e.filters(null)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(caseOf(e.f.ConstructTrue()))))
}
