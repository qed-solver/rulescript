package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneOrdinalityCols(t *testing.T) {
	rule := loadRule(t, "PruneOrdinalityCols")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	ordinal := e.f.Metadata().AddColumn("ordinal", types.Int)
	out := e.f.Metadata().AddColumn("out", types.Int)
	ordItem := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), ordinal),
		}
	}
	topItem := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), e.v(ordinal)), out),
		}
	}
	ord := e.f.ConstructProject(s, ordItem(), opt.MakeColSet(cols...))
	before := e.f.ConstructProject(ord, topItem(), opt.ColSet{})
	pruned := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0], cols[1]))
	wantOrd := e.f.ConstructProject(pruned, ordItem(), opt.MakeColSet(cols[0], cols[1]))
	want := e.f.ConstructProject(wantOrd, topItem(), opt.ColSet{})
	e.expectRewrite(rule, before, want)

	wrongOrd := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[2])), ordinal),
	}, opt.MakeColSet(cols...))
	e.expectNoMatch(rule, e.f.ConstructProject(wrongOrd, topItem(), opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(ord, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[2]), e.v(ordinal)), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, ord)
}

func TestSQLPruneOrdinalityCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneOrdinalityCols", "SELECT a+b+z FROM (SELECT a,b,c,a+b AS z FROM (VALUES (1,2,3),(4,5,6)) AS v(a,b,c)) AS q", true)
}
