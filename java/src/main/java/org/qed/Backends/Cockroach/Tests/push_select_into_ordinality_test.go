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
	s, cols := e.scan("u", 2)
	ordinal := e.f.Metadata().AddColumn("ordinal", types.Int)
	ordItem := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), ordinal),
		}
	}
	ord := e.f.ConstructProject(s, ordItem(), opt.MakeColSet(cols...))
	before := e.f.ConstructSelect(ord, e.filters(e.f.ConstructGt(e.v(cols[0]), e.num(0))))
	filtered := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(cols[0]), e.num(0))))
	want := e.f.ConstructProject(filtered, ordItem(), opt.MakeColSet(cols...))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(ord, e.filters(e.f.ConstructGt(e.v(ordinal), e.num(0)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(ord, e.filters(e.f.ConstructGt(e.v(cols[1]), e.num(0)))))
	e.expectNoMatch(rule, ord)
}

func TestSQLPushSelectIntoOrdinality(t *testing.T) {
	expectSQLFiresOrNot(t, "PushSelectIntoOrdinality", "SELECT * FROM (SELECT a,b,a+b AS z FROM (VALUES (1,2),(3,4)) AS v(a,b)) AS q WHERE a>0", true)
}
