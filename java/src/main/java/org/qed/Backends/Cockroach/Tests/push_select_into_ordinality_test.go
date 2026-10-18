package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushSelectIntoOrdinality(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoOrdinality")
	e := newEnv(t)
	input, cols := e.scan("nn", 2)
	ordCol := e.f.Metadata().AddColumn("ordinality", types.Int)
	numbered := func(from memo.RelExpr) memo.RelExpr {
		return e.f.ConstructProject(from, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), ordCol),
		}, opt.MakeColSet(cols[0], cols[1]))
	}
	boundFilter := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(7))) }
	before := e.f.ConstructSelect(numbered(input), boundFilter())
	want := numbered(e.f.ConstructSelect(input, boundFilter()))
	e.expectRewrite(rule, before, want)

	onOrdinality := e.f.ConstructSelect(numbered(input), e.filters(e.f.ConstructEq(e.v(ordCol), e.num(7))))
	e.expectNoMatch(rule, onOrdinality)
}
